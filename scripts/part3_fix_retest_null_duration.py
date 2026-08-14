#!/usr/bin/env python3
from pathlib import Path

# Retest nullable duration: PostgreSQL must know the JDBC NULL type.
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

# Global Search: mistake has last_seen_at, not updated_at.
p=Path('server/src/main/kotlin/com/veltrix/hom/vnext/server/SearchAggregators.kt')
s=p.read_text(encoding='utf-8')
order_decl='        val orderCol=if(table=="mistake")"last_seen_at" else "updated_at"\n'
anchor='        val projectFilter=if(p!=null && projectCol!=null)" AND $projectCol=?::uuid" else if(p!=null && table=="project")" AND id=?::uuid" else ""\n'
if order_decl not in s:
    if anchor not in s: raise SystemExit('search order declaration anchor missing')
    s=s.replace(anchor,anchor+order_decl,1)
old_order='ORDER BY updated_at DESC NULLS LAST LIMIT ?'
new_order='ORDER BY $orderCol DESC NULLS LAST LIMIT ?'
if new_order not in s:
    if old_order not in s: raise SystemExit('search ORDER BY anchor missing')
    s=s.replace(old_order,new_order,1)
    p.write_text(s,encoding='utf-8')
    print('PART3_SEARCH_MISTAKE_ORDER_FIX=PASS')
else:
    p.write_text(s,encoding='utf-8')
    print('PART3_SEARCH_MISTAKE_ORDER_ALREADY_FIXED=PASS')
