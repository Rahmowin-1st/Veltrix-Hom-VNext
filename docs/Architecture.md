# Architecture

Veltrix Hom vNext Part 1 is split into Android presentation/foundation, reusable Kotlin domain logic, Ktor services, PostgreSQL persistence, contracts, scripts, and documentation. Public Ktor routes call repositories/services; deterministic business rules remain outside Compose UI. Production AI, storage, embedding, OCR and translation are adapter boundaries. Part 2 economy and Part 3 final presentation are intentionally outside this foundation.
