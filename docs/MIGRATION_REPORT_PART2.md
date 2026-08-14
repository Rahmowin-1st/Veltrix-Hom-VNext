# Migration Report — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Migration order is V001→V006. V005 establishes the complete Part 2 game-account schema without deleting Part 1 data. V006 is additive hardening: Store refunds, seasonal derived statistics/achievements and game state notifications. No destructive rewrite of V001–V004 is used.

Flyway runs during `Database` construction with migration naming validation. Manager-acceptance tests use a fresh real PostgreSQL service so V005/V006 are executed before repository tests. The final package records test logs and exact source SHA.

Android Room note: the runtime-tested Android source emits a compiler warning because `room.schemaLocation` is not configured, so generated Room schema JSON is not committed. This acceptance correction does not change Android/Room schema or migrations; existing Android migration/runtime tests from run 31787591097 are preserved by exact Android/core tree-diff provenance. Deterministic Room schema export remains an explicit tooling limitation, not a hidden claim; see `KNOWN_LIMITATIONS.md`.
