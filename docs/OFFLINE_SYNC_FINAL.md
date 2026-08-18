# Offline / Sync — Final

Existing `VeltrixLocalDatabase` remains the core sync source of truth. `Part3LocalDatabase` is a separate Part 3 read-model/context/event DB for snapshots, ContextCarry and semantic-event consumption.

`Part3AndroidRepository` reads local snapshots first and returns FRESH/STALE/OFFLINE, then refreshes/persists revisions when network is available. ContextCarry uses local-first queueing, WorkManager unique work, server ownership validation, expected revision and idempotency.

Final Android CI executes API36 runtime, offline and process-death against real Ktor/PostgreSQL. Offline AI/provider success is not fabricated.