#!/usr/bin/env python3
import json, urllib.request, urllib.error, uuid, hashlib, sys
BASE='http://127.0.0.1:8080'
def req(method,path,body=None,token=None,expect=(200,201,202)):
    data=None if body is None else json.dumps(body).encode()
    r=urllib.request.Request(BASE+path,data=data,method=method,headers={'Content-Type':'application/json','Accept':'application/json','X-Request-ID':'ci-'+str(uuid.uuid4())})
    if token:r.add_header('Authorization','Bearer '+token)
    try:
        with urllib.request.urlopen(r,timeout=30) as resp:
            raw=resp.read().decode(); code=resp.status
    except urllib.error.HTTPError as e:
        raw=e.read().decode();code=e.code
    if code not in expect: raise RuntimeError(f'{method} {path} -> {code} {raw[:500]}')
    return raw,json.loads(raw) if raw and raw.lstrip().startswith(('{','[')) else None
s=uuid.uuid4().hex[:8]
_,session=req('POST','/v1/auth/register',{'login':f'http-{s}@example.test','password':'testing-password-12345','displayName':'HTTP Runtime'},expect=(201,))
token=session['sessionToken']
_,project=req('POST','/v1/projects',{'title':'CEFR C1 HTTP','purpose':'runtime smoke'},token,(201,))
req('PUT',f"/v1/projects/{project['id']}/instructions",{'body':'Use British English. Correct grammar. Prefer CEFR C1 vocabulary.','structuredJson':'{}'},token)
_,chat=req('POST','/v1/chats',{'scope':'PROJECT','projectId':project['id'],'title':'HTTP Brain','learningMode':'TUTOR'},token,(201,))
text='CEFR C1 source fixture: British English spells colour and practising phrasal verbs improves fluency.'
h=hashlib.sha256(text.encode()).hexdigest()
_,source=req('POST','/v1/sources',{'title':'HTTP source','type':'TEXT','mimeType':'text/plain','contentHash':h,'sizeBytes':len(text)},token,(201,))
_,source=req('POST',f"/v1/sources/{source['id']}/text",{'text':text},token)
assert source['state']=='READY',source
req('POST',f"/v1/sources/{source['id']}/link-project",{'projectId':project['id']},token)
_,hits=req('POST','/v1/sources/search',{'query':'British English colour','sourceIds':[source['id']],'limit':5},token)
assert hits and hits[0]['citation']['sourceId']==source['id']
# SSE uses the same real context orchestration but deterministic TEST_ONLY model provider.
body={'conversationId':chat['id'],'projectId':project['id'],'sourceIds':[source['id']],'text':'Explain colour from the selected source','learningMode':'TUTOR','memoryEnabled':True,'projectMemoryEnabled':True,'toolIds':['text.count'],'toolInputs':{'text.count':{'text':'one two three'}},'idempotencyKey':'http-ai-'+s+'-12345678'}
data=json.dumps(body).encode();r=urllib.request.Request(BASE+'/v1/ai/stream',data=data,method='POST',headers={'Authorization':'Bearer '+token,'Content-Type':'application/json','Accept':'text/event-stream','X-Request-ID':'ci-sse-'+s})
with urllib.request.urlopen(r,timeout=45) as resp:
    sse=resp.read().decode()
assert resp.status==200
assert 'event: done' in sse or '"final":true' in sse,sse[-1000:]
_,msgs=req('GET',f"/v1/chats/{chat['id']}/messages",None,token)
assistant=[m for m in msgs if m['role']=='ASSISTANT' and m['state']=='COMPLETED']
assert assistant
_,cit=req('GET',f"/v1/chats/{chat['id']}/messages/{assistant[-1]['id']}/citations",None,token)
assert cit and cit[0]['citation']['sourceId']==source['id']
# Project context isolation: unrelated GLOBAL conversation can be created without project scope.
_,global_chat=req('POST','/v1/chats',{'scope':'GLOBAL','title':'Global'},token,(201,))
assert global_chat['projectId'] is None
print('HTTP_RUNTIME_GATE=PASS project=%s source=%s chat=%s citations=%d'%(project['id'],source['id'],chat['id'],len(cit)))
