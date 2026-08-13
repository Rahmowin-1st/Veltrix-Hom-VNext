# Backend Current State

Scope: Veltrix Hom vNext Part 1 core/intelligence + Android app-foundation only. Store economy and final frontend visual polish are outside Part 1 and remain explicit placeholders.

Current durable source is the `veltrix-hom-part1-completion` branch. Implemented boundaries include Kotlin core/domain rules, Ktor HTTP services, PostgreSQL/pgvector persistence, S3-compatible source storage, retrieval/citation provenance, assessments, deep practice, flashcards, mistakes, goals, notes, translation/calculator tool contracts, sync/idempotency, Android Room persistence and Android developer-harness routes.

Verified historical gates must be mapped to their exact commit. The final Android API36 normal-services runtime/package gate is intentionally not marked VERIFIED in this file until a final exact-head workflow run succeeds. Compile/target SDK remains 37; API36 is the stable functional emulator boundary because the hosted API37 image previously showed framework instability.

Canonical details: `Feature_Inventory.md`, `Architecture.md`, `Database_Schema.md`, `Security_Report.md`, `Performance_Report.md`, `Known_Limitations.md`, `contracts/openapi.yaml`.