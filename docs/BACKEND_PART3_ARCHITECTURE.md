# Backend Part 3 Architecture — Final Capability Expansion

Veltrix Hom vNext remains a Kotlin modular backend: deterministic core policy, Ktor application/API boundary, PostgreSQL durable authority, and Android local-first read/sync foundation.

- `core/.../Part3FinalSystems.kt`: Student Model validation, personalization inputs, Home priorities/insights, Universal Command, long-term level gate, Map/reward rules, avatar catalog and Store policy.
- `server/.../Part3StudentRepository.kt`: signals, correction/state/delete, recommendations, ContextCarry and source relationships.
- `Part3SnapshotRepository.kt`: Home, Personal and Project Workspace v3 aggregates.
- `Part3ExperienceRepository.kt`: project templates/customization, Map/season/avatar/events/timeline.
- `Part3CompletionRepository.kt`: versioned Learning Modes, goal graph/links/suggestions, assessment history/retest, note versions and account data control.
- `Part3FinalRepository.kt`: application facade.
- Android `Part3LocalDatabase.kt`, `Part3AndroidContracts.kt`, `SyncWorker.kt`: typed snapshots, ContextCarry, frontend events and durable sync.

PostgreSQL/Ktor remain authoritative for ownership, permissions, revisions, progression, Coins, Store, Map, seasons and destructive account operations. AI may recommend/draft but cannot own those rules. Migrations are additive V001–V009; OpenAPI is generated/drift-checked.

Release truth requires the canonical exact-SHA Source Audit, Final Gate and Runtime Package to be GREEN; `PROVENANCE.txt`/`SHA256SUMS.txt` bind evidence.