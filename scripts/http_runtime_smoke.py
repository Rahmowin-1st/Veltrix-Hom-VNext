#!/usr/bin/env python3
import json, re, sys, threading, time, uuid
from urllib import request, error

BASE=(sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8080').rstrip('/')

def call(method,path,payload=None,token=None,expected=(200,),headers=None,raw=False,timeout=30):
    body=None
    h={'Accept':'application/json'}
    if payload is not None:
        body=json.dumps(payload).encode(); h['Content-Type']='application/json'
    if token: h['Authorization']='Bearer '+token
    if headers: h.update(headers)
    req=request.Request(BASE+path,data=body,headers=h,method=method)
    try:
        with request.urlopen(req,timeout=timeout) as r:
            data=r.read(); status=r.status
    except error.HTTPError as e:
        data=e.read(); status=e.code
    if status not in expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {status}: {data[:1000]!r}')
    if raw: return status,data.decode(errors='replace')
    if not data: return None
    return json.loads(data)

def multipart(path,fields,file_field,filename,mime,file_bytes,token):
    boundary='----veltrix-ci-'+uuid.uuid4().hex
    chunks=[]
    for k,v in fields.items():
        chunks += [f'--{boundary}\r\n'.encode(),f'Content-Disposition: form-data; name="{k}"\r\n\r\n'.encode(),str(v).encode(),b'\r\n']
    chunks += [f'--{boundary}\r\n'.encode(),f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'.encode(),f'Content-Type: {mime}\r\n\r\n'.encode(),file_bytes,b'\r\n',f'--{boundary}--\r\n'.encode()]
    body=b''.join(chunks)
    req=request.Request(BASE+path,data=body,headers={'Authorization':'Bearer '+token,'Content-Type':f'multipart/form-data; boundary={boundary}','Accept':'application/json'},method='POST')
    with request.urlopen(req,timeout=30) as r:
        assert r.status==201,(r.status,r.read())
        return json.loads(r.read())

def stream(token,chat_id,text,key,project_id=None,source_ids=None,request_id=None):
    payload={'conversationId':chat_id,'text':text,'learningMode':'TUTOR','idempotencyKey':key,'sourceIds':source_ids or []}
    if project_id: payload['projectId']=project_id
    headers={'X-Request-ID':request_id} if request_id else {}
    _,body=call('POST','/v1/ai/stream',payload,token,(200,),headers,raw=True,timeout=45)
    segments=[]; message_id=None
    for line in body.splitlines():
        if line.startswith('data: '):
            try:
                obj=json.loads(line[6:])
                if 'segment' in obj: segments.append(obj['segment'])
                if obj.get('messageId'): message_id=obj['messageId']
            except json.JSONDecodeError:
                pass
    return body,''.join(segments),message_id

health=call('GET','/health'); assert health['status']=='ok'
ready=call('GET','/ready'); assert ready['ready'] is True and ready['database']=='ok' and ready['storageConfigured'] is True and ready['embeddingConfigured'] is True
assert ready['testAiConfigured'] is True and ready['aiConfigured'] is False

login=f'ci-{int(time.time())}-{uuid.uuid4().hex[:8]}@example.test'
session=call('POST','/v1/auth/register',{'login':login,'password':'testing-password-12345','displayName':'Veltrix CI'},expected=(201,))
token=session['sessionToken']; account=session['accountId']
assert len(token)>=32 and len(account)==36

global_chat=call('POST','/v1/chats',{'scope':'GLOBAL','title':'CI Memory','learningMode':'TUTOR'},token,(201,))
body,full,assistant_id=stream(token,global_chat['id'],'I prefer concise explanations for grammar.','ci-memory-'+uuid.uuid4().hex)
assert 'TEST_ONLY:' in full and 'ctx:project=none' in full and assistant_id

memory_item=None
for _ in range(30):
    memories=call('GET','/v1/memory?limit=100',token=token)
    memory_item=next((m for m in memories if 'concise explanations' in m['statement'].lower()),None)
    if memory_item: break
    time.sleep(1)
assert memory_item, 'automatic post-chat memory did not materialize'
corrected=call('POST',f"/v1/memory/{memory_item['id']}/correct",{'statement':'Prefer compact bullet explanations for grammar','evidenceObjectId':assistant_id},token)
assert corrected['status']=='USER_CORRECTED'

project=call('POST','/v1/projects',{'title':'CEFR C1 CI','purpose':'runtime evidence'},token,(201,))
instruction=call('PUT',f"/v1/projects/{project['id']}/instructions",{'body':'Use British English. Correct my grammar. Prefer CEFR C1 vocabulary.'},token)
assert instruction['active'] is True

phrase='cedar nebula calibration 4829 proves hybrid retrieval provenance'
source=multipart('/v1/sources/upload',{'title':'CI RAG Fixture','type':'FILE','mimeType':'text/plain'},'file','ci-rag.txt','text/plain',('Veltrix source evidence. '+phrase+'. This fixture is deterministic.').encode(),token)
for _ in range(60):
    source=call('GET',f"/v1/sources/{source['id']}",token=token)
    if source['state']=='READY': break
    if source['state'] in ('FAILED','UNSUPPORTED'): raise AssertionError(source)
    time.sleep(1)
assert source['state']=='READY',source
storage=call('GET',f"/v1/sources/{source['id']}/storage",token=token)
assert storage['provider'].lower()=='s3' and storage['key'] and storage['size']>0 and storage['sha256']

call('POST',f"/v1/sources/{source['id']}/link-project",{'projectId':project['id']},token)
hits=call('POST','/v1/sources/search',{'query':'cedar nebula calibration','sourceIds':[source['id']],'projectId':project['id'],'limit':5},token)
assert hits and hits[0]['citation']['sourceId']==source['id'] and hits[0]['fusedScore']>0

project_chat=call('POST','/v1/chats',{'scope':'PROJECT','projectId':project['id'],'title':'CI Project Brain','learningMode':'TUTOR'},token,(201,))
body,full,project_assistant=stream(token,project_chat['id'],'Use my grammar explanation preference. Explain the cedar nebula calibration evidence.','ci-project-ai-'+uuid.uuid4().hex,project['id'],[source['id']])
assert f"ctx:project={project['id']}" in full,full
assert ';instruction=none' not in full and ';mode=TUTOR' in full
mem=int(re.search(r';mem=(\d+)',full).group(1)); cites=int(re.search(r';citations=(\d+)',full).group(1))
assert mem>=1 and cites>=1,(mem,cites,full)
citations=call('GET',f"/v1/chats/{project_chat['id']}/messages/{project_assistant}/citations",token=token)
assert citations and any(c['citation']['sourceId']==source['id'] for c in citations)

body,global_full,_=stream(token,global_chat['id'],'Use my grammar explanation preference.','ci-global-isolation-'+uuid.uuid4().hex)
assert 'ctx:project=none' in global_full and ';instruction=none' in global_full and ';citations=0' in global_full
assert int(re.search(r';mem=(\d+)',global_full).group(1))>=1

note_id=str(uuid.uuid4()); mutation_id=str(uuid.uuid4()); idem='ci-note-'+uuid.uuid4().hex
batch={'mutations':[{'mutationId':mutation_id,'entityType':'NOTE','entityId':note_id,'operation':'UPSERT','idempotencyKey':idem,'payload':{'title':'Offline queued note','body':'exactly once runtime proof','projectId':project['id']}}]}
first=call('POST','/v1/sync/mutations',batch,token); replay=call('POST','/v1/sync/mutations',batch,token)
assert first['results'][0]['status']=='APPLIED'
assert replay['results'][0]['status']=='APPLIED' and replay['results'][0]['code']=='IDEMPOTENT_REPLAY'
notes=call('GET','/v1/notes?limit=200',token=token); assert sum(1 for n in notes if n['id']==note_id)==1

second=call('POST','/v1/auth/register',{'login':f'b-{uuid.uuid4().hex}@example.test','password':'testing-password-12345','displayName':'Isolation B'},expected=(201,))
token_b=second['sessionToken']
call('GET',f"/v1/projects/{project['id']}",token=token_b,expected=(404,))
call('GET',f"/v1/sources/{source['id']}",token=token_b,expected=(404,))
b_hits=call('POST','/v1/sources/search',{'query':'cedar nebula calibration','sourceIds':[source['id']],'limit':5},token_b)
assert b_hits==[]

cancel_chat=call('POST','/v1/chats',{'scope':'GLOBAL','title':'CI Cancel','learningMode':'TUTOR'},token,(201,))
rid='ci-cancel-'+uuid.uuid4().hex; result={}
def run_slow():
    result['stream']=stream(token,cancel_chat['id'],'CI_SLOW_STREAM_MARKER cancellation proof','ci-cancel-msg-'+uuid.uuid4().hex,request_id=rid)
thread=threading.Thread(target=run_slow); thread.start(); time.sleep(.35)
cancelled=call('POST','/v1/ai/cancel',{'requestId':rid},token); assert cancelled['cancelled'] is True,cancelled
thread.join(15); assert not thread.is_alive()
cancel_body=result['stream'][0]
assert 'event: error' in cancel_body and 'AI_CANCELLED' in cancel_body,cancel_body

call('GET','/v1/store',token=token,expected=(501,))
print(json.dumps({'HTTP_RUNTIME_SMOKE':'PASS','accountId':account,'projectId':project['id'],'sourceId':source['id'],'automaticMemoryId':corrected['id'],'hybridHits':len(hits),'projectCitations':cites,'syncReplay':'IDEMPOTENT_REPLAY','crossAccountIsolation':'PASS','sseCancellation':'PASS','liveAiConfigured':ready['aiConfigured'],'testAiConfigured':ready['testAiConfigured']},sort_keys=True))
