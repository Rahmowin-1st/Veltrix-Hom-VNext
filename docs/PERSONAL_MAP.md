# Personal Map

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Personal Map eligibility is deterministic: level >=5 and Memory maturity `SUFFICIENT` or `STRONG`, unless an existing map state is already active/completed. The server computes maturity from Part 1 Memory and level from authoritative XP projection; client cannot unlock by sending those values.

Durable state uses `personal_map`, `map_generation_record`, `map_unit_progress`, map definitions/units/dependencies and season linkage. `map()` returns eligibility/state/visible unit projection; `POST /personal/map/unlock` creates only when eligible.

Executed proof: core `personalMapRequiresLevelAndMemoryGate`, server/Android Store+Map contract smoke, and Map generation validation tests.
