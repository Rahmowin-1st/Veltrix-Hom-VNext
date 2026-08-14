#!/usr/bin/env python3
import json, os, time, uuid, urllib.request, urllib.error

BASE=os.environ.get("VELTRIX_API_BASE_URL","http://127.0.0.1:8080").rstrip("/")

def call(method,path,body=None,token=None,expect=(200,)):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,method=method)
    req.add_header("Accept","application/json")
    req.add_header("X-Request-ID","part2-smoke-"+uuid.uuid4().hex)
    if data is not None:req.add_header("Content-Type","application/json")
    if token:req.add_header("Authorization","Bearer "+token)
    try:
        with urllib.request.urlopen(req,timeout=10) as r:
            text=r.read().decode();code=r.status
    except urllib.error.HTTPError as e:
        text=e.read().decode();code=e.code
    if code not in expect: raise AssertionError(f"{method} {path}: HTTP {code}: {text}")
    return json.loads(text) if text else None

suffix=uuid.uuid4().hex[:10]
session=call("POST","/v1/auth/register",{"login":f"part2-smoke-{suffix}@example.test","password":"testing-password-12345","displayName":"Part2 Runtime"},expect=(201,))
token=session["sessionToken"]
profile=call("GET","/v1/game/profile",token=token)
assert profile["level"]==1 and profile["lifetimeXp"]==0 and profile["coinBalance"]==0
store=call("GET","/v1/store",token=token)
assert store["catalogVersion"]=="store-v1" and any(i["itemId"]=="avatar-pro-focus" for i in store["items"])
map_state=call("GET","/v1/personal/map",token=token)
assert map_state["eligibility"]["levelRequirement"]==5
assert map_state["eligibility"]["memoryRequirement"]=="SUFFICIENT_OR_STRONG"
project=call("POST","/v1/projects",{"title":"Part2 runtime project","purpose":"real reward pipeline runtime proof"},token,(201,))
assert project["id"]
rewarded=False
for _ in range(40):
    time.sleep(.5)
    profile=call("GET","/v1/game/profile",token=token)
    if profile["lifetimeXp"]>0 and profile["coinBalance"]>0:
        rewarded=True;break
assert rewarded,"reward worker did not process meaningful Part1 event"
recon=call("GET","/v1/game/coins/reconciliation",token=token)
assert recon["matches"] is True
season=call("GET","/v1/seasons/current",token=token)
assert season is not None
exported=call("GET","/v1/account/export",token=token)
for key in ("progressionProfiles","coinAccounts","coinLedger","inventoryOwnership","equippedAvatars","gamingStatistics","gameStateEvents"):
    assert key in exported["entityCounts"],f"missing Part2 export key: {key}"
assert exported["entityCounts"]["progressionProfiles"]==1
call("POST","/v1/account/delete",{"password":"testing-password-12345","confirmation":"DELETE"},token,(200,))
call("GET","/v1/game/profile",token=token,expect=(401,))
call("POST","/v1/auth/login",{"login":f"part2-smoke-{suffix}@example.test","password":"testing-password-12345","deviceLabel":"after-delete"},expect=(401,))
print("PART2_HTTP_ACCOUNT_EXPORT_DELETE=PASS")
print("PART2_HTTP_RUNTIME_SMOKE=PASS")
