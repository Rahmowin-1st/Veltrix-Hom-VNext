#!/usr/bin/env python3
from pathlib import Path
p=Path('server/src/main/kotlin/com/veltrix/hom/vnext/server/Part3CompletionRepository.kt')
s=p.read_text(encoding='utf-8')
changed=False
old='"progression_profile","xp_ledger","coin_account_projection","coin_ledger","daily_activity_state"'
new='"progression_profile","xp_ledger","coin_account_projection","coin_ledger","reward_grant","reward_decision_log","activity_reward_queue","daily_activity_state"'
if old in s:
    s=s.replace(old,new,1);changed=True
elif new not in s:
    raise SystemExit('export table anchor missing')
anchor='payloads[table]=json;counts[table]=count };val canonical='
patch='''payloads[table]=json;counts[table]=count };val aliases=linkedMapOf(
            "progressionProfiles" to "progression_profile","xpLedger" to "xp_ledger","coinAccounts" to "coin_account_projection","coinLedger" to "coin_ledger",
            "rewardGrants" to "reward_grant","rewardDecisions" to "reward_decision_log","rewardQueue" to "activity_reward_queue","dailyActivity" to "daily_activity_state",
            "consistencyState" to "consistency_state","consistencyHistory" to "consistency_history","achievementProgress" to "achievement_progress",
            "inventoryOwnership" to "inventory_ownership","equippedAvatars" to "equipped_avatar","storePurchases" to "store_purchase","storeRefunds" to "store_refund",
            "personalMaps" to "personal_map","mapGenerations" to "map_generation_record","mapUnitProgress" to "map_unit_progress","seasonProgress" to "season_progress",
            "gamingStatistics" to "gaming_statistics","gameStateEvents" to "game_state_event"
        );aliases.forEach{(alias,table)->counts[alias]=counts.getValue(table)};val canonical='''
if anchor in s:
    s=s.replace(anchor,patch,1);changed=True
elif '"progressionProfiles" to "progression_profile"' not in s:
    raise SystemExit('export alias anchor missing')
if changed:
    p.write_text(s,encoding='utf-8')
    print('PART3_EXPORT_COMPAT_PATCH=PASS')
else:
    print('PART3_EXPORT_COMPAT_ALREADY_APPLIED=PASS')
