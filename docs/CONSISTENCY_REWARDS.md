# Consistency Rewards

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Consistency counts qualified meaningful local dates, not app opens. Durable state is split into `consistency_state` and daily `consistency_history`; timezone used is recorded. The engine advances current/longest consistency only for a new qualified local day and includes a 20-hour safety window for daily bonus behavior after timezone changes.

V006 derives `consistency-7` achievement state from longest consistency. No notification trigger pressures a user to preserve a streak; game notifications are state-change notices only. Executed proof includes `Part2GameSystemsTest.consistencyCountsQualifiedLocalDaysNotAppOpens` plus real reward integration.
