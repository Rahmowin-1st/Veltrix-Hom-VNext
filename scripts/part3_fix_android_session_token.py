#!/usr/bin/env python3
from pathlib import Path
p=Path('android/app/src/main/kotlin/com/veltrix/hom/vnext/Part3AndroidContracts.kt')
s=p.read_text(encoding='utf-8')
changes=[]
count=s.count('session.accessToken')
if count:
    s=s.replace('session.accessToken','session.token')
    changes.append(f'token={count}')
old='private fun JSONArray.stringList():List<String>=List(length()){i->getString(i)}'
new='private fun JSONArray.stringList():List<String> = List(length()) { i -> getString(i) }'
if old in s:
    s=s.replace(old,new)
    changes.append('stringList-spacing=1')
if changes:
    p.write_text(s,encoding='utf-8')
    print('PART3_ANDROID_COMPILER_FIX=' + ','.join(changes))
else:
    print('PART3_ANDROID_COMPILER_FIX_ALREADY_APPLIED=PASS')
