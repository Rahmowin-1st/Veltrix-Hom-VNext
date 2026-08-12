#!/usr/bin/env python3
import subprocess, yaml, re, pathlib, sys
root=pathlib.Path(__file__).resolve().parents[1]
route_lines=subprocess.check_output(['python3',str(root/'scripts/route_inventory.py')],cwd=root,text=True).splitlines()
routes=[]
for line in route_lines:
    method,path=line.split(' ',1)
    if path.startswith('/v1'): path=path[3:] or '/'
    routes.append((method.lower(),path))
p=root/'contracts/openapi.yaml'
d=yaml.safe_load(p.read_text())
paths=d.setdefault('paths',{})
verbs={'get','post','put','patch','delete'}
def op_id(method,path):
    words=re.findall(r'[A-Za-z0-9]+',path.replace('{','').replace('}',''))
    base=method+'_'+'_'.join(words)
    return re.sub(r'[^A-Za-z0-9_]','_',base)
for method,path in routes:
    item=paths.setdefault(path,{})
    if method not in item:
        op={'operationId':op_id(method,path),
            'responses':{'200':{'description':'Successful response'},'400':{'$ref':'#/components/responses/Error'},'401':{'$ref':'#/components/responses/Error'},'500':{'$ref':'#/components/responses/Error'}}}
        if path in ('/health','/ready'):
            op['security']=[];op['servers']=[{'url':'/'}]
        item[method]=op
# ensure operation IDs unique and every path parameter declared
seen={}
for path,item in paths.items():
    for method,op in list(item.items()):
        if method not in verbs or not isinstance(op,dict): continue
        oid=op.setdefault('operationId',op_id(method,path))
        if oid in seen:
            op['operationId']=oid+'_'+str(len(seen))
        seen[op['operationId']]=(method,path)
        params={m.group(1) for m in re.finditer(r'\{([^}]+)\}',path)}
        existing={x.get('name') for x in op.get('parameters',[]) if isinstance(x,dict) and x.get('in')=='path'}
        existing|={x.get('name') for x in item.get('parameters',[]) if isinstance(x,dict) and x.get('in')=='path'}
        missing=params-existing
        if missing:
            op.setdefault('parameters',[]).extend({'name':x,'in':'path','required':True,'schema':{'type':'string','format':'uuid'}} for x in sorted(missing))
# remove stale checkpoint text from ai stream
if '/ai/stream' in paths and 'post' in paths['/ai/stream']:
    paths['/ai/stream']['post']['description']='SSE AI execution through the Veltrix context planner, provider router, source grounding, deterministic tools, citation persistence, and post-response jobs. Test providers are test-only; production never fakes model output.'
# deterministic order: preserve existing YAML insertion order; safe_dump keeps it.
p.write_text(yaml.safe_dump(d,sort_keys=False,allow_unicode=True,width=140))
