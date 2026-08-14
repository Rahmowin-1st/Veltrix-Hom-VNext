# Event Integration

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Part 1 `activity_event` remains the learning/product activity source. V005 adds evidence/revision and an AFTER INSERT trigger that queues meaningful events into `activity_reward_queue`. Reward processing creates decisions/grants/ledgers and emits account-scoped `game_state_event` records with causation/correlation, source event, resulting revision, schema version and idempotency key.

State event types include progression/economy/inventory/avatar/map/achievement transitions. V006 creates only non-manipulative SYSTEM_NOTICE intents for LEVEL_UP, ACHIEVEMENT_UNLOCKED, MAP_UNLOCKED, UNIT_COMPLETED and ITEM_ACQUIRED. There is no client-supplied reward event path.
