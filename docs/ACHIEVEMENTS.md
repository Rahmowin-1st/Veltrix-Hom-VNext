# Achievements

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Definitions are versioned in `achievement_definition`; per-account durable progress is `achievement_progress`. V005 seeds meaningful-event achievements. V006 retires the inert `map-pathfinder-1` definition and adds derived achievements `consistency-7`, `map-pathfinder-2` and `season-first` with database refresh functions/triggers.

Unlock/claim transitions update gaming/season statistics and emit `ACHIEVEMENT_UNLOCKED` game-state events. State-change notifications are idempotently derived from those events. Executed proof: `Part2CompletionIntegrationTest.ledgersStoreRefundSeasonAndNotificationsAreDurableAndIdempotent` and core achievement/catalog tests.
