# Part 3 Frontend Handoff

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Part 3 may redesign presentation, but must treat these backend contracts as authoritative: game profile/revisions; XP/Coin histories; Store catalog+purchase; achievements/inventory/avatars/equip; Personal Map eligibility/state/unit ordering; seasons/statistics/events; account export/delete.

Frontend must never compute or override XP, Coin balance, Store price, ownership, achievement unlock, Map eligibility, season transition or expected-revision conflict resolution. Use `contracts/openapi.yaml`, `contracts/part2-backend-contract.yaml`, `PART2_FRONTEND_CONTRACT.md`, `ANDROID_PART2_CONTRACTS.md` and DTOs as integration inputs.

Offline UI may show cached revisioned snapshots, but economic mutations require server confirmation. Handle 401/403/409/429/503 domain errors explicitly. Part 3 must not weaken authentication/idempotency/revision rules for visual convenience.
