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
    print('PART3_SEARCH_MISTAKE_ORDER_FIX=PASS')
else:
    print('PART3_SEARCH_MISTAKE_ORDER_ALREADY_FIXED=PASS')

# Store search must use the canonical Part 2 schema: category/description live in inventory metadata,
# while store_item owns price, availability, requirements and active/catalog binding only.
old_store='''SELECT si.item_id,coalesce(si.category,ic.item_type),coalesce(si.description,ic.metadata::text),si.price_coins FROM store_item si JOIN store_catalog sc ON sc.catalog_version=si.catalog_version AND sc.active=true JOIN inventory_catalog ic ON ic.item_id=si.item_id WHERE si.active=true AND (lower(si.item_id) LIKE lower(?) OR lower(coalesce(si.category,ic.item_type)) LIKE lower(?) OR lower(coalesce(si.description,ic.metadata::text)) LIKE lower(?)) ORDER BY si.price_coins,si.item_id LIMIT ?'''
new_store='''SELECT si.item_id,ic.item_type,ic.metadata::text,si.price_coins FROM store_item si JOIN store_catalog sc ON sc.catalog_version=si.catalog_version AND sc.active=true JOIN inventory_catalog ic ON ic.item_id=si.item_id AND ic.active=true WHERE si.active=true AND (lower(si.item_id) LIKE lower(?) OR lower(ic.item_type) LIKE lower(?) OR lower(ic.metadata::text) LIKE lower(?)) ORDER BY si.price_coins,si.item_id LIMIT ?'''
if new_store in s:
    print('PART3_SEARCH_STORE_SCHEMA_ALREADY_FIXED=PASS')
elif old_store in s:
    s=s.replace(old_store,new_store,1)
    print('PART3_SEARCH_STORE_SCHEMA_FIX=PASS')
else:
    raise SystemExit('store search SQL anchor missing')
p.write_text(s,encoding='utf-8')
