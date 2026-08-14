# Test Report — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

### Reverified on final handoff SHA
- `:core:test` and `:server:test` with fresh real PostgreSQL/Flyway V001–V006.
- progression policy export check and Part 1 structural regression.
- current Ktor readiness + Part 2 HTTP smoke + performance smoke.
- Manager acceptance tests: concurrent no-overspend, concurrent multi-device avatar equip convergence, season rollover lifetime preservation, Part 2 export/delete, avatar ownership/equip restart+relogin persistence.
- OpenAPI drift: route inventory equals generated contract.
- final doc completeness/stale-truth scan and package SHA recomputation.

### Preserved unchanged Android proof
Run `31787591097`, job `94726891516`, SHA `2e70908...`: Android build targetSdk37, API36 runtime, install, cold launch, full connected suite 15/15, offline, process-death, Part2 targeted instrumentation 2/2. Final CI must prove Android/core/build inputs are byte-source unchanged before copying those exact APK/test APK bytes and evidence.

The exact final run/job and results are in package `PROVENANCE.txt` and `evidence/`; no source doc pre-guesses the new GitHub run ID.
