# Seasons

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`season_definition` is versioned with PLANNED/ACTIVE/CLOSED/ARCHIVED state, time window and identity metadata. `season_progress` stores account-specific units, achievements, XP, Coins, participation and revision. Lifetime XP/Coin/inventory live in separate account tables and are not reset by season rollover.

`reconcileSeasons()` takes a PostgreSQL advisory transaction lock, closes expired active definitions+progress and activates planned definitions whose time window has started; each transition is recorded idempotently in `season_rollover_execution`.

Executed Manager proof `seasonRolloverPreservesLifetimeProgressionCoinsInventoryAndClosesSeasonProgress` creates an expired and planned season in real PostgreSQL, runs rollover, checks close/start states and proves lifetime XP, level, Coin balance and inventory are preserved/reconciled.
