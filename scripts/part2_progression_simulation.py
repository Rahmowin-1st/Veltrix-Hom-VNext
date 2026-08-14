#!/usr/bin/env python3
import argparse, json, math
from pathlib import Path

RULES={
"PROJECT_CREATED":{"baseXp":25,"baseCoins":4,"softDailyLimit":1,"hardDailyLimit":2},
"GOAL_COMPLETED":{"baseXp":45,"baseCoins":8,"softDailyLimit":4,"hardDailyLimit":7},
"SOURCE_ADDED":{"baseXp":20,"baseCoins":3,"softDailyLimit":3,"hardDailyLimit":5},
"TEST_COMPLETED":{"baseXp":55,"baseCoins":10,"softDailyLimit":2,"hardDailyLimit":4},
"QUIZ_COMPLETED":{"baseXp":45,"baseCoins":8,"softDailyLimit":4,"hardDailyLimit":7},
"PRACTICE_COMPLETED":{"baseXp":35,"baseCoins":6,"softDailyLimit":4,"hardDailyLimit":7},
"FLASHCARD_REVIEW_COMPLETED":{"baseXp":15,"baseCoins":2,"softDailyLimit":5,"hardDailyLimit":8},
"MISTAKE_RESOLVED":{"baseXp":40,"baseCoins":8,"softDailyLimit":4,"hardDailyLimit":7},
"NOTE_CREATED":{"baseXp":10,"baseCoins":1,"softDailyLimit":3,"hardDailyLimit":5},
"MEANINGFUL_CHAT_SESSION":{"baseXp":20,"baseCoins":3,"softDailyLimit":2,"hardDailyLimit":4},
}
PROFILES={
"LIGHT":{"effectiveDailyXp":55,"rawAttemptedDailyXp":55},
"REGULAR":{"effectiveDailyXp":145,"rawAttemptedDailyXp":145},
"HIGH_ACTIVITY":{"effectiveDailyXp":260,"rawAttemptedDailyXp":260},
"ABUSIVE":{"effectiveDailyXp":450,"rawAttemptedDailyXp":1800},
}
HORIZONS=[7,30,90,180,365]

def threshold(level:int)->int:
    if level==1:return 0
    n=float(level-1)
    return round(100*n**1.85+150*n)

def level_for(xp:int)->int:
    return max(i for i in range(1,51) if threshold(i)<=xp)

def build():
    profiles={k:dict(v) for k,v in PROFILES.items()}
    simulations={}
    for name,p in profiles.items():
        daily=min(p["effectiveDailyXp"],450)
        p["minimumDaysToLevel50"]=math.ceil(threshold(50)/daily)
        simulations[name]=[]
        for days in HORIZONS:
            raw=p["rawAttemptedDailyXp"]*days
            effective=daily*days
            simulations[name].append({"days":days,"rawAttemptedXp":raw,"effectiveXp":effective,"blockedOrSuppressedXp":raw-effective,"level":level_for(effective)})
    return {
        "policyVersion":"reward-v1","levelCurveVersion":"level-curve-v1",
        "dailyXpHardCap":450,"dailyCoinHardCap":90,"dailyBonusXp":20,"dailyBonusCoins":5,
        "rules":RULES,
        "levelThresholds":[{"level":i,"cumulativeXp":threshold(i)} for i in range(1,51)],
        "simulationProfiles":profiles,"simulations":simulations,
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--check",type=Path)
    ap.add_argument("--write",type=Path)
    args=ap.parse_args()
    data=build()
    if args.check:
        existing=json.loads(args.check.read_text())
        if existing!=data:
            raise SystemExit("progression policy export is stale")
        print("PART2_PROGRESSION_POLICY_CHECK=PASS")
    elif args.write:
        args.write.write_text(json.dumps(data,indent=2)+"\n")
    else:
        print(json.dumps(data,indent=2))

if __name__=="__main__":main()
