#!/usr/bin/env python3
from pathlib import Path
p=Path('server/src/main/kotlin/com/veltrix/hom/vnext/server/Part3CompletionRepository.kt')
s=p.read_text(encoding='utf-8')
old="CASE WHEN ? IS NULL THEN NULL ELSE now()+(?||' seconds')::interval END"
new="CASE WHEN ?::integer IS NULL THEN NULL ELSE now()+make_interval(secs => ?::integer) END"
if new in s:
    print('PART3_RETEST_NULL_DURATION_ALREADY_FIXED=PASS')
elif old in s:
    s=s.replace(old,new,1)
    p.write_text(s,encoding='utf-8')
    print('PART3_RETEST_NULL_DURATION_FIX=PASS')
else:
    raise SystemExit('retest SQL anchor missing')
