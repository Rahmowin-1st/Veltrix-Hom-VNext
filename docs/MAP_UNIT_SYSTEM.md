# Map Unit System

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Foundation Map v1 contains five ordered units: START, LEARN, IMPROVE, KNOWLEDGE, SYNTHESIZE. Dependencies form u1→u2→u3→u4→u5. Completion criteria and reward definitions are stored server-side. Future units remain HIDDEN/LOCKED until prerequisites are satisfied; only the next valid unit becomes AVAILABLE/IN_PROGRESS.

Map unit mutation uses expected revision and server lookup of account-owned map progress. Unit completion updates season progress/statistics and derived achievements through durable triggers. Core tests explicitly prove hidden-future-unit behavior and Map content dependency validity.
