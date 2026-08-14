# Store Economy

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

The active Store catalog is `store-v1`. V005 currently exposes PRO (350 Coins, level 5), ELITE (700, level 10), ULTRA (1500, level 20) and HYPERPRO (3200, level 35). Requirements and price come from PostgreSQL, never from client payload.

Purchase transaction: lock Coin projection, idempotency replay check, read active catalog+requirements, verify level/ownership/balance, insert purchase, append spend ledger, guarded projection decrement, grant inventory, link ledger+entitlement revision, update statistics, emit COINS_SPENT and ITEM_ACQUIRED.

V006 refund transaction restores the authoritative purchase price, records a REFUND ledger entry, removes Store-owned entitlement, resets equipped refunded avatar to default when needed, and marks purchase REFUNDED. Real-Postgres tests cover all of these plus concurrent purchase race/no overspend.
