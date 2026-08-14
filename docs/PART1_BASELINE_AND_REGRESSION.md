# Part 1 Baseline and Regression

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Part 2 is based on frozen verified Part 1 commit `fe4b2528a0a7da550351d926d44cc025e49dcafc`. The Part 2 branch must keep that commit as an ancestor; the final CI checks the merge-base relationship and runs Part 1 structural/regression coverage before packaging.

Part 1 account/auth, projects, sources/RAG, memory, practice, sync/offline, settings, notifications and Android foundation are retained. Part 2 adds game-account tables and APIs; it does not replace Part 1 state machines. The Manager-acceptance correction modifies only Part 2 account-export completeness, acceptance tests, generated OpenAPI metadata/routes and handoff/provenance packaging.

Evidence: final `evidence/provenance.txt`, `evidence/part2-delta.txt`, `evidence/backend-tests.txt`; preserved Android run `31787591097` includes the full 15-test connected instrumentation suite.
