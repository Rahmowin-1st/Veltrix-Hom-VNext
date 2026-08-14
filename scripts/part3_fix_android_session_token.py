#!/usr/bin/env python3
from pathlib import Path
p=Path('android/app/src/main/kotlin/com/veltrix/hom/vnext/Part3AndroidContracts.kt')
s=p.read_text(encoding='utf-8')
old='session.token'
count=s.count(old)
if count==0:
    print('PART3_ANDROID_SESSION_TOKEN_ALREADY_FIXED=PASS')
else:
    s=s.replace(old,'session.accessToken')
    p.write_text(s,encoding='utf-8')
    print(f'PART3_ANDROID_SESSION_TOKEN_FIXED={count}')
