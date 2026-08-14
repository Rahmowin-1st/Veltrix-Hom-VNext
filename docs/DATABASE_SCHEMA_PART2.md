# Database Schema — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Part 2 migrations are additive on V001–V004.

### V005 — `V005__part2_game_account.sql`
Adds evidence/revision to activity events; reward policy; progression profile; XP/Coin ledgers and Coin projection; reward grant/decision/queue; daily/consistency state; achievements; inventory/avatar/equipped avatar; Store catalogs/purchases; seasons; map definitions/units/dependencies/personal maps/generation/progress; season progress; gaming statistics; game-state events; rollover executions; seed policy/catalog/map/season data.

Key invariants: account FKs use `ON DELETE CASCADE`; Coin projection balance cannot be negative; ledger/event/idempotency uniqueness is account-scoped; map dependencies/ownership are relationally constrained; Store price and requirements are server/database data.

### V006 — `V006__part2_completion.sql`
Adds `store_refund`, `season_progress.participated`, corrected derived achievement versions, active-season XP/Coin accumulation, derived achievement refresh functions/triggers, season statistics, and idempotent state-change notification intents.

Final CI boots a fresh PostgreSQL 17 + pgvector database and Flyway applies/validates the complete migration chain before tests.
