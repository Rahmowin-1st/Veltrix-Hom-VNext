# API / OpenAPI — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

`contracts/openapi.yaml` and `docs/API_Contract.openapi.yaml` are generated from the exact Ktor `/v1` route inventory by `scripts/generate_openapi_contract.py`. Current metadata is Part 2 version `0.2.0-part2`; the generated inventory includes game profile/XP/Coins/reconciliation/stats/events, achievements, inventory, avatars/equip, Personal Map/unlock/unit start, seasons, Store/purchase and account export/delete. CI runs `scripts/verify_openapi_drift.py` and requires operation-count parity.
