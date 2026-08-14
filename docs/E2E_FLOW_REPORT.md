# E2E Flow Report A–S

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

## Closure matrix

| ID | Acceptance behavior | Exact executed test/gate | Source | Environment | Evidence |
|---|---|---|---|---|---|
| A | Frozen Part 1 baseline/regression | `scripts/check_part1_structure.py`; full core/server regression | current final SHA | GitHub Ubuntu 24.04 + PostgreSQL 17 | `evidence/part1-structure.txt`, `evidence/backend-tests.txt` |
| B | Meaningful activity → exactly-once XP/Coins | `Part2PostgresIntegrationTest.meaningfulEventRewardsExactlyOnceAndSemanticDuplicateIsRejected` | current final SHA | real PostgreSQL | server JUnit XML + `evidence/backend-tests.txt` |
| C | Anti-farming/caps/duplicate rejection | `Part2GameSystemsTest` anti-farming cases + Part2Postgres integration | current final SHA | Kotlin/JVM + real PostgreSQL | core/server JUnit XML |
| D | Level 1–50 curve and long-horizon simulation | `levelCurveIsMonotonicAndHasExactBoundaries`; `progressionSimulationCoversRequiredHorizonsAndAbuseCap` | current final SHA | Kotlin/JVM | core JUnit XML + `evidence/progression-policy.txt` |
| E | Coin ledger/reconciliation/refund/reversal | `Part2CompletionIntegrationTest.ledgersStoreRefundSeasonAndNotificationsAreDurableAndIdempotent` | current final SHA | real PostgreSQL | server JUnit XML |
| F | Store purchase race / no overspend | `Part2ManagerAcceptanceIntegrationTest.concurrentPurchaseRaceAllowsNoOverspendAndReconciles` | current final SHA | real PostgreSQL, two concurrent workers | `evidence/manager-e2e.xml` |
| G | Achievement + derived achievement durability | `Part2CompletionIntegrationTest.ledgersStoreRefundSeasonAndNotificationsAreDurableAndIdempotent` | current final SHA | real PostgreSQL | server JUnit XML |
| H | Avatar ownership → equip → restart/relogin | `Part2ManagerAcceptanceIntegrationTest.avatarOwnershipEquipSurvivesServerRestartAndRelogin` | current final SHA | real PostgreSQL, DB pool restart + new login | `evidence/manager-e2e.xml` |
| I | Personal Map level+Memory eligibility gate | `Part2GameSystemsTest.personalMapRequiresLevelAndMemoryGate` + Part2 HTTP/Android contract smoke | current + preserved Android SHA | JVM/Ktor + Android API36 | core XML, `evidence/http-smoke.txt`, preserved runtime |
| J | Map generation validation + deterministic fallback | `Part2CompletionSystemsTest.invalidAiMapFallsBackAndAiHasNoEconomicAuthority` and related tests | current final SHA | Kotlin/JVM | core JUnit XML |
| K | Sequential Map units / future units hidden | `Part2GameSystemsTest.mapUnitProgressOnlyExposesTheNextUnit` + deterministic map structure test | current final SHA | Kotlin/JVM | core JUnit XML |
| L | Consistency qualified-day semantics | `Part2GameSystemsTest.consistencyCountsQualifiedLocalDaysNotAppOpens` | current final SHA | Kotlin/JVM | core JUnit XML |
| M | Season rollover + lifetime preservation | `Part2ManagerAcceptanceIntegrationTest.seasonRolloverPreservesLifetimeProgressionCoinsInventoryAndClosesSeasonProgress` | current final SHA | real PostgreSQL | `evidence/manager-e2e.xml` |
| N | Gaming statistics/events/state-change notifications | `Part2CompletionIntegrationTest.ledgersStoreRefundSeasonAndNotificationsAreDurableAndIdempotent` | current final SHA | real PostgreSQL | server JUnit XML |
| O | Offline/reopen cache stale-revision safety | `Part2GameInstrumentedTest.gameCacheSurvivesDatabaseReopenAndRejectsStaleRevision`; OfflineData tests | 2e70908... preserved | Android 16/API36 emulator | preserved run 31787591097 runtime evidence |
| P | Process-death persistence | `Part2GameProcessDeathInstrumentedTest` seed + fresh instrumentation verify | 2e70908... preserved | Android 16/API36 emulator | preserved run 31787591097 process-death evidence |
| Q | Multi-device concurrency consistency | `Part2ManagerAcceptanceIntegrationTest.multiDeviceConcurrentAvatarEquipHasSingleWinnerAndConverges` | current final SHA | real PostgreSQL, two concurrent repository clients | `evidence/manager-e2e.xml` |
| R | Part 2 account export/delete handling | `Part2ManagerAcceptanceIntegrationTest.part2AccountExportIncludesOwnedGameStateAndDeleteRevokesRelogin` | current final SHA | real PostgreSQL + auth relogin | `evidence/manager-e2e.xml` |
| S | Full integration/runtime/provenance/package closure | full core/server current tests + preserved 15-test Android suite + final truth/hash gates | current final + preserved Android SHA | GitHub CI + Android API36 preserved | `PROVENANCE.txt`, `SHA256SUMS.txt`, final status/evidence |

**Result rule:** a row is PASS only when the named CI execution succeeds. The final package adds `evidence/E2E_EXECUTION_STATUS.tsv` generated from the exact run; this source document defines the mapping but does not fabricate a future run result.
