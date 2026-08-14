# Coin Ledger and Reconciliation

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`coin_ledger` entries are typed `GRANT|SPEND|REFUND|REVERSAL|ADJUSTMENT`, account-scoped and idempotency-key unique. Purchase links use deferred FK provenance to `store_purchase`; V006 adds `store_refund` with purchase/key uniqueness. Projection balance has a database `CHECK(balance >= 0)`.

`Part2GameRepository.reconcileCoins` compares projected balance with `sum(coin_ledger.amount)`. `Part2CompletionRepository` provides controlled correction/refund operations. Executed real-PostgreSQL tests assert replay behavior, refund restoration, underflow rejection, reconciliation and concurrent no-overspend.
