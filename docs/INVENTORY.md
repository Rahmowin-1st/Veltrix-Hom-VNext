# Inventory

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`inventory_catalog` is global product data; `inventory_ownership` is account-owned durable entitlement state with source, acquisition time, optional season scope, metadata and revision. Unique ownership is enforced by the account/item primary key. Sources include DEFAULT, LEVEL_UNLOCK, ACHIEVEMENT, MAP_UNIT, SEASON_REWARD, STORE_PURCHASE and ADMIN_MIGRATION.

Store purchase inserts ownership in the same transaction as spend; refund removes only ownership whose source is `STORE_PURCHASE`. Default avatar ownership is materialized by `ensureAccount`. Executed proof covers purchase/refund ownership and Manager restart/relogin avatar persistence.
