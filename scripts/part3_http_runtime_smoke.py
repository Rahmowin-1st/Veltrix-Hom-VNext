#!/usr/bin/env python3
import json, os, uuid, urllib.request, urllib.error

BASE=os.environ.get('VELTRIX_API_BASE_URL','http://127.0.0.1:8080').rstrip('/')

def call(method,path,body=None,token=None,expect=(200,)):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,method=method)
    req.add_header('Accept','application/json')
    req.add_header('X-Request-ID','part3-smoke-'+uuid.uuid4().hex)
    if data is not None:req.add_header('Content-Type','application/json')
    if token:req.add_header('Authorization','Bearer '+token)
    try:
        with urllib.request.urlopen(req,timeout=12) as r:
            text=r.read().decode();code=r.status
    except urllib.error.HTTPError as e:
        text=e.read().decode();code=e.code
    if code not in expect: raise AssertionError(f'{method} {path}: HTTP {code}: {text}')
    return json.loads(text) if text else None

suffix=uuid.uuid4().hex[:10]
login=f'part3-smoke-{suffix}@example.test'
password='testing-password-12345'
session=call('POST','/v1/auth/register',{'login':login,'password':password,'displayName':'Part3 Runtime'},expect=(201,))
token=session['sessionToken'];account=session['accountId']

modes=call('GET','/v1/learning-modes',token=token)
ids={m['id'] for m in modes}
assert {'TUTOR','SOCRATIC','EXAM','REVIEW','CONCISE','DEEP_DIVE'} <= ids

home=call('GET','/v1/home',token=token)
personal=call('GET','/v1/personal',token=token)
assert home['accountId']==account and home['schemaVersion']==3
assert personal['accountId']==account and personal['schemaVersion']==3

project=call('POST','/v1/projects',{'title':f'Physics {suffix}','purpose':'Part3 HTTP runtime'},token,(201,))
pid=project['id']
workspace=call('GET',f'/v1/projects/{pid}/workspace',token=token)
assert workspace['project']['id']==pid and workspace['schemaVersion']==3

g1=call('POST',f'/v1/projects/{pid}/goals',{'title':'Learn mechanics'},token,(201,))
g2=call('POST',f'/v1/projects/{pid}/goals',{'title':'Pass mechanics test'},token,(201,))
dep=call('POST',f'/v1/projects/{pid}/goals/{g2["id"]}/dependencies',{'dependsOnGoalId':g1['id']},token,(201,))
assert dep['dependsOnGoalId']==g1['id']

deps=call('GET',f'/v1/projects/{pid}/goals/{g2["id"]}/dependencies',token=token)
assert len(deps)==1
sug=call('POST',f'/v1/projects/{pid}/goal-suggestions',{'parentGoalId':g1['id'],'title':'Solve mixed problems','provenanceJson':'{"source":"AI_DRAFT"}'},token,(201,))
accepted=call('POST',f'/v1/projects/{pid}/goal-suggestions/{sug["id"]}/decision',{'decision':'ACCEPT','expectedRevision':sug['revision']},token)
assert accepted['state']=='ACCEPTED' and accepted['acceptedGoalId']

signal=call('POST','/v1/student-model/signals',{'projectId':pid,'type':'GOAL','valueJson':'{"focus":"mechanics"}','evidence':[{'kind':'USER_STATEMENT','objectId':'http-smoke'}]},token,(201,))
model=call('GET',f'/v1/student-model?projectId={pid}',token=token)
assert any(s['id']==signal['id'] for s in model['signals'])

ctx=call('PUT','/v1/context-carry',{'projectId':pid,'topic':'mechanics','learningMode':'TUTOR','origin':'PROJECT','expectedRevision':0},token)
assert ctx['projectId']==pid and ctx['contextRevision']>=1
cmd=call('POST','/v1/commands/resolve',{'text':f'Open my Physics {suffix} project','projectId':pid},token)
assert cmd['deterministic'] is True

results=call('POST','/v1/search',{'query':'Physics','limit':50},token)
assert any(r['type']=='PROJECT' and r['id']==pid for r in results)

exported=call('GET','/v1/account/export',token=token)
assert exported['schemaVersion']==3 and exported['entityCounts']['project']>=1
assert pid in exported['entityPayloads']['project']
assert len(exported['payloadSha256'])==64

call('POST','/v1/account/delete',{'password':password,'confirmation':'DELETE'},token)
call('GET','/v1/home',token=token,expect=(401,))
call('POST','/v1/auth/login',{'login':login,'password':password,'deviceLabel':'after-delete'},expect=(401,))

print('PART3_HTTP_LEARNING_MODES=PASS')
print('PART3_HTTP_HOME_PERSONAL_WORKSPACE=PASS')
print('PART3_HTTP_GOAL_GRAPH=PASS')
print('PART3_HTTP_STUDENT_CONTEXT_COMMAND_SEARCH=PASS')
print('PART3_HTTP_EXPORT_DELETE=PASS')
print('PART3_HTTP_RUNTIME_SMOKE=PASS')
