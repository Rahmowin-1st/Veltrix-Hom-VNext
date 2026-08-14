# Observability — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Auditable Part 2 state is carried by durable records rather than opaque client state: `reward_decision_log`, XP/Coin ledgers, `reward_grant`, `store_purchase`, `store_refund`, `map_generation_record`, `game_state_event`, `season_rollover_execution`, projection revisions and notification intents.

Ktor readiness/health, structured domain errors and CI evidence expose operational state. Final package evidence contains provenance, backend test logs, HTTP/performance smoke, E2E XML, preserved Android runtime mapping and hash manifests. No production telemetry backend is claimed by this handoff beyond the observability surfaces implemented in source.
