#!/usr/bin/env python3
from __future__ import annotations
import json, re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DOCS=ROOT/'docs'
required='''PART2_ARCHITECTURE.md PART1_BASELINE_AND_REGRESSION.md PROGRESSION_ENGINE.md LEVEL_CURVE_V1.md REWARD_POLICY_V1.md PROGRESSION_SIMULATION.md ANTI_FARMING.md COIN_ECONOMY.md COIN_LEDGER_AND_RECONCILIATION.md CONSISTENCY_REWARDS.md ACHIEVEMENTS.md INVENTORY.md AVATAR_SYSTEM.md STORE_ECONOMY.md PERSONAL_MAP.md MAP_GENERATION.md MAP_UNIT_SYSTEM.md SEASONS.md GAMING_STATISTICS.md EVENT_INTEGRATION.md DATABASE_SCHEMA_PART2.md MIGRATION_REPORT_PART2.md ANDROID_PART2_CONTRACTS.md OFFLINE_SYNC_PART2.md SECURITY_PART2.md PERFORMANCE_PART2.md OBSERVABILITY_PART2.md TEST_REPORT_PART2.md E2E_FLOW_REPORT.md PART3_FRONTEND_HANDOFF.md KNOWN_LIMITATIONS.md VERIFIED_IMPLEMENTED_NOT_VERIFIED.md'''.split()
errors=[]
for n in required:
    p=DOCS/n
    if not p.is_file(): errors.append(f'missing:{n}')
    elif p.stat().st_size < 250: errors.append(f'too-short:{n}:{p.stat().st_size}')
curve=DOCS/'level-curve-v1.json'
if not curve.is_file(): errors.append('missing:level-curve-v1.json')
else:
    c=json.loads(curve.read_text())
    pol=json.loads((DOCS/'progression-policy-v1.json').read_text())
    if c.get('version') != 'level-curve-v1' or c.get('thresholds') != pol.get('levelThresholds') or len(c.get('thresholds',[])) != 50:
        errors.append('level-curve-export-drift')
for mig,needles in {
    'V005__part2_game_account.sql':['CREATE TABLE progression_profile','CREATE TABLE coin_ledger','CREATE TABLE store_purchase','CREATE TABLE personal_map','CREATE TABLE season_progress'],
    'V006__part2_completion.sql':['CREATE TABLE IF NOT EXISTS store_refund','part2_touch_active_season','part2_refresh_derived_achievements'],
}.items():
    text=(ROOT/'database/migrations'/mig).read_text()
    for n in needles:
        if n not in text: errors.append(f'migration-missing:{mig}:{n}')
settings=(ROOT/'server/src/main/kotlin/com/veltrix/hom/vnext/server/SettingsAccountRepositories.kt').read_text()
for key in ['progressionProfiles','xpLedger','coinAccounts','coinLedger','achievementProgress','inventoryOwnership','equippedAvatars','storePurchases','storeRefunds','personalMaps','mapGenerations','mapUnitProgress','seasonProgress','gamingStatistics','gameStateEvents']:
    if f'"{key}"' not in settings: errors.append(f'account-export-missing:{key}')
test=(ROOT/'server/src/test/kotlin/com/veltrix/hom/vnext/server/Part2ManagerAcceptanceIntegrationTest.kt').read_text()
methods=['concurrentPurchaseRaceAllowsNoOverspendAndReconciles','multiDeviceConcurrentAvatarEquipHasSingleWinnerAndConverges','seasonRolloverPreservesLifetimeProgressionCoinsInventoryAndClosesSeasonProgress','part2AccountExportIncludesOwnedGameStateAndDeleteRevokesRelogin','avatarOwnershipEquipSurvivesServerRestartAndRelogin']
for m in methods:
    if f'fun {m}' not in test: errors.append(f'manager-test-missing:{m}')
openapi_text=(ROOT/'contracts/openapi.yaml').read_text()
if 'version: 0.2.0-part2' not in openapi_text: errors.append('openapi-version-not-part2')
for p in ['/game/profile','/game/coins/reconciliation','/store','/store/purchase','/avatars/equip','/personal/map','/seasons/current','/account/export','/account/delete']:
    if f'  {p}:' not in openapi_text: errors.append(f'openapi-path-missing:{p}')
if (ROOT/'docs/API_Contract.openapi.yaml').read_bytes() != (ROOT/'contracts/openapi.yaml').read_bytes(): errors.append('openapi-doc-copy-drift')
# Current final-status truth scan. Phrases are forbidden in package-facing current docs/contracts.
for root in [DOCS, ROOT/'contracts']:
    for p in root.rglob('*'):
        if not p.is_file() or p.suffix.lower() not in {'.md','.txt','.yaml','.yml','.json'}: continue
        text=p.read_text(errors='ignore')
        forbidden=[r'No Part 2 economy',r'Part 2 deferred',r'Part 2 out of scope',r'Scope:\s*Part 1',r'deferred to Part 2',r'Part 2 economy/map behavior remains intentionally unavailable',r'intentionally not implemented in Part 1',r'Part 2 placeholder']
        for pat in forbidden:
            if re.search(pat,text,re.I): errors.append(f'stale-truth:{p.relative_to(ROOT)}:{pat}')
if errors:
    print('PART2_MANAGER_HANDOFF_CHECK=FAIL')
    for e in errors: print(e)
    sys.exit(1)
print(f'PART2_REQUIRED_HANDOFF_DOCS={len(required)}')
print('PART2_LEVEL_CURVE_MACHINE_EXPORT=PASS')
print('PART2_MIGRATION_DOC_SOURCE=V005+V006')
print('PART2_OPENAPI_CURRENT=PASS')
print('PART2_STALE_TRUTH_SCAN=PASS')
print('PART2_MANAGER_HANDOFF_CHECK=PASS')
