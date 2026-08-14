# Security — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Security invariants: authenticated account principal on all game/store/account routes; account_id filters on repository queries; server-derived rewards/prices/balances; no privileged secrets in APK; idempotent mutation keys; expected-revision conflicts; PostgreSQL row locks/check constraints; account-scoped FKs and unique constraints.

Anti-farming rejects trivial/no-evidence/duplicate/capped reward attempts. Store purchase prevents overspend even under concurrent requests. Avatar equip verifies ownership. Account deletion requires explicit `DELETE` confirmation plus password re-authentication and revokes sessions; login excludes `deleted_at` accounts.

Executed proof: account isolation, anti-farming, purchase race, multi-device equip conflict/convergence, export/delete+relogin denial. No weakening of authentication or economic validation was introduced for acceptance.
