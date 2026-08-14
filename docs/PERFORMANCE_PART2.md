# Performance — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Baseline final run 31787591097 measured real Ktor/PostgreSQL Part 2 profile smoke: p50 8.03 ms, p95 9.50 ms, p99 9.71 ms in GitHub-hosted CI. These are CI smoke measurements, not production SLO claims.

Concurrency safety intentionally serializes same-account Coin spends/equip rows at narrow database boundaries. The Manager correction adds tests/documentation and account-export counts; it does not introduce a new hot-path query in game profile/store/reward processing. Final CI reruns affected server tests and HTTP smoke on the exact final source.
