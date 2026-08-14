# Part 2 Architecture

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

## Boundary
Part 2 extends the frozen Part 1 foundation additively. Android remains an untrusted client/cache; Kotlin core owns deterministic reward/level/map rules; Ktor repositories own authenticated mutations; PostgreSQL owns durable ledgers, projections, idempotency and concurrency.

## Runtime components
- `core/Part2GameSystems.kt`: Level 1–50 curve, meaningful-activity classifier, reward policy, Personal Map eligibility, consistency and season boundary rules.
- `core/Part2CompletionSystems.kt`: structured Map generation schema/validator, deterministic provider and AI-content-only fallback policy, progression simulations.
- `server/Part2GameRepository.kt`: authoritative profile, XP/Coin histories, Store purchase, inventory/avatar equip, Personal Map/units, seasons, statistics and game-state events.
- `server/Part2CompletionRepository.kt`: controlled XP/Coin corrections, reconciliation, reversals and Store refunds.
- `database/migrations/V005__part2_game_account.sql` + `V006__part2_completion.sql`: all Part 2 durable state and hardening triggers.
- Android `Part2GameClient` + `Part2GameCache`: network projection and revision-aware local cache; no client authority over XP, Coins, prices or entitlements.

## Trust rules
Client input selects actions/items and supplies idempotency/expected-revision tokens. The server derives price, balance, eligibility and rewards. PostgreSQL row locks, unique keys and checks enforce no overspend and replay safety. AI may shape validated Map content only; it cannot assign economic values, permissions or entitlements.

## Evidence
Current-source core/server tests run against real PostgreSQL in the final Manager-acceptance CI. Preserved Android API36 proof is reused only after the final workflow proves no diff in Android/core/build inputs from the runtime-tested SHA. See `TEST_REPORT_PART2.md`, `E2E_FLOW_REPORT.md` and package `evidence/`.
