# Coin Economy

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Coins are server-authoritative. `coin_ledger` is the immutable accounting history; `coin_account_projection` is the fast non-negative projection with earned/spent totals and revision. Grants come from validated reward processing or controlled operations; spends come from Store purchases; refunds/reversals are explicit ledger types.

Clients never provide authoritative balance, price or refund amount. Store price is read from the active catalog. Projection/ledger consistency is exposed through reconciliation and tested after grants, purchases, refunds, underflow attempts and concurrent races.
