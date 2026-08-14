# Gaming Statistics

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`gaming_statistics` is an account projection for meaningful activities, XP earned, Coins earned/spent, achievements unlocked, units/maps completed and seasons participated, with revision. V006 season participation and derived achievement functions update these counters transactionally.

Statistics are read through `GET /v1/game/stats`. They are not a source of monetary truth: XP and Coin ledgers/projections remain authoritative. Real PostgreSQL completion tests verify season participation, Store spending/refund effects and derived achievements.
