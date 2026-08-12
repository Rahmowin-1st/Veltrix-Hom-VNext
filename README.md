# Veltrix Hom vNext

New-generation Android-first Veltrix Hom product. This repository is intentionally isolated from the legacy Veltrix Hom codebase.

## Modules
- `android/` native Kotlin + Jetpack Compose application foundation
- `server/` versioned API / orchestration foundation
- `core/` presentation-independent domain/business logic
- `contracts/` OpenAPI and shared schemas
- `database/` PostgreSQL migrations and policies
- `docs/` architecture, handoff, security, tests, limitations
- `scripts/` deterministic bootstrap/verification helpers

Package/application ID: `com.veltrix.hom.vnext`
