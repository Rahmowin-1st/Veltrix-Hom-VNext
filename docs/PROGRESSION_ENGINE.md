# Progression Engine

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`LevelCurveV1` and `RewardPolicyV1` are deterministic Kotlin core engines. Lifetime XP is append-ledger-derived state, projected into `progression_profile`. Level is recomputed from lifetime XP; no client can send an authoritative level or XP grant.

Meaningful events enter `activity_reward_queue`. Reward processing validates semantic evidence/object identity, rejects trivial events and semantic duplicates, applies per-category soft/hard limits and global daily XP/Coin caps, then writes `reward_decision_log`, `reward_grant`, `xp_ledger`, `coin_ledger` and projections in a transaction. Unique `(account_id,idempotency_key)` and event/policy keys make replay explicit.

Source: `core/.../Part2GameSystems.kt`, `server/.../Part2GameRepository.kt`, migration V005. Executed proof: `Part2GameSystemsTest`, `Part2PostgresIntegrationTest.meaningfulEventRewardsExactlyOnceAndSemanticDuplicateIsRejected`, progression simulation check.
