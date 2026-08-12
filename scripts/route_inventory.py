#!/usr/bin/env python3
import re, sys, pathlib, json
p=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt')
lines=p.read_text().splitlines()
depth=0
stack=[] # (block_depth, prefix)
routes=[]
verbs=('get','post','put','patch','delete')

def clean(s):
    s=s.replace('/$API_VERSION','/v1').replace('$API_VERSION','v1')
    s=re.sub(r'//.*$','',s)
    return s

def norm(prefix, sub):
    x=(prefix.rstrip('/')+'/'+sub.lstrip('/')) if sub else prefix
    x=re.sub(r'//+','/',x)
    return x or '/'
for raw in lines:
    line=clean(raw)
    # Drop scopes that ended on prior line. Route block body begins at depth+1.
    while stack and depth < stack[-1][0]: stack.pop()
    prefix=stack[-1][1] if stack else ''
    mr=re.search(r'\broute\("([^"]*)"\)\s*\{',line)
    if mr:
        rp=norm(prefix,mr.group(1))
        opens=line.count('{')-line.count('}')
        stack.append((depth+1,rp))
        prefix=rp
    for v in verbs:
        for m in re.finditer(r'\b'+v+r'\("([^"]*)"\)',line):
            routes.append((v.upper(),norm(prefix,m.group(1))))
        # route("/x") { get { ... } }
        if re.search(r'\b'+v+r'\s*\{',line) and not re.search(r'\b'+v+r'\("',line):
            routes.append((v.upper(),prefix or '/'))
    depth += line.count('{')-line.count('}')
    while stack and depth < stack[-1][0]: stack.pop()
# De-duplicate preserving order
seen=set(); out=[]
for x in routes:
    if x not in seen: seen.add(x);out.append(x)
if '--json' in sys.argv: print(json.dumps([{'method':m,'path':p} for m,p in out],indent=2))
else:
    for m,p in out: print(f'{m} {p}')
