#!/usr/bin/env python3
import json, os, statistics, time, uuid, urllib.request

BASE=os.environ.get("VELTRIX_API_BASE_URL","http://127.0.0.1:8080").rstrip("/")
def call(method,path,body=None,token=None):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,method=method)
    req.add_header("Accept","application/json")
    req.add_header("X-Request-ID","part2-perf-"+uuid.uuid4().hex)
    if data is not None:req.add_header("Content-Type","application/json")
    if token:req.add_header("Authorization","Bearer "+token)
    started=time.perf_counter()
    with urllib.request.urlopen(req,timeout=10) as r:
        payload=r.read();code=r.status
    elapsed=(time.perf_counter()-started)*1000
    if code not in (200,201):raise AssertionError((code,payload))
    return json.loads(payload) if payload else None,elapsed

suffix=uuid.uuid4().hex[:10]
session,_=call("POST","/v1/auth/register",{"login":f"part2-perf-{suffix}@example.test","password":"testing-password-12345","displayName":"Part2 Perf"})
token=session["sessionToken"]
# Warm server/DB/JIT path before measuring.
for _ in range(10):call("GET","/v1/game/profile",token=token)
samples=[]
for _ in range(60):
    _,ms=call("GET","/v1/game/profile",token=token);samples.append(ms)
samples.sort()
p95=samples[max(0,int(len(samples)*0.95)-1)]
p99=samples[max(0,int(len(samples)*0.99)-1)]
print(f"PART2_PROFILE_LATENCY_P50_MS={statistics.median(samples):.2f}")
print(f"PART2_PROFILE_LATENCY_P95_MS={p95:.2f}")
print(f"PART2_PROFILE_LATENCY_P99_MS={p99:.2f}")
# Local CI is not an Internet SLA; this is a regression/no-freeze guard against second-scale backend stalls.
if p95>=1000:raise SystemExit(f"Part2 profile p95 regression: {p95:.2f}ms")
print("PART2_PERFORMANCE_SMOKE=PASS")
