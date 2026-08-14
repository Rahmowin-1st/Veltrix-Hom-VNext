# Anti-Farming

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Anti-farming is deterministic and layered: meaningful-activity classification; semantic evidence validation; semantic duplicate detection; per-event soft/hard daily limits; global XP/Coin daily caps; idempotent reward queue/decision/grant/ledger keys; server-derived values only.

The database is the final race boundary. Reward, purchase and correction idempotency keys are account-scoped unique constraints. Store purchase locks the Coin projection row `FOR UPDATE`, reads authoritative catalog price, rechecks balance and performs an update guarded by `balance>=price`.

Executed proof includes core anti-farming tests, real-Postgres reward replay/semantic-duplicate tests, and Manager test `concurrentPurchaseRaceAllowsNoOverspendAndReconciles`.
