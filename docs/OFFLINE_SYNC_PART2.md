# Offline and Sync — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Part 2 account state remains server-authoritative. Android caches remote snapshots by account/kind with `serverRevision`; stale revisions cannot overwrite newer cached data. Part 1 sync mutation infrastructure remains responsible for offline first-party content mutations and is not replaced by a client-side game ledger.

Game economic mutations require online server confirmation; there is no offline Coin spend that later tries to merge balances. This avoids divergent economic authority. On reconnect/relogin, clients re-fetch authoritative projections and revisioned state.

Executed evidence: preserved Android `Part2GameInstrumentedTest.gameCacheSurvivesDatabaseReopenAndRejectsStaleRevision`, `OfflineDataInstrumentedTest`, process-death tests; current real-Postgres multi-device concurrency Manager test proves server convergence.
