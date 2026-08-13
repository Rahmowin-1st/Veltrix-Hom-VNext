# Backend Architecture

Part 1 uses Android presentation/app-foundation + reusable Kotlin domain/core + Ktor service layer + PostgreSQL/pgvector state + S3-compatible object storage + explicit provider adapters.

Deterministic rules, permissions, learning calculations and sync semantics live outside Compose UI. Ktor routes delegate to services/repositories. PostgreSQL is authoritative durable server state; object storage owns uploaded/generated file bodies; vectors/chunks retain provenance. Android is an untrusted client with Room-backed local state, offline-safe mutations, process-recreation durability and WorkManager sync.

AI/embedding/OCR/translation are server-side adapter boundaries; provider credentials are never APK-owned. Retrieval outputs preserve source/version/chunk provenance. Store/economy belongs to Part 2; final visual composition belongs to Part 3.

Canonical architecture detail remains in `Architecture.md`, `Repository_Map.md`, `Android_Domain_Contracts.md`, `AI_Context_Pipeline.md`, `Hybrid_RAG.md`, `Offline_Sync.md`, and `Database_Schema.md`.