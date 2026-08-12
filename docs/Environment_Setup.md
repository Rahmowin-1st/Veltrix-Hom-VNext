# Environment Setup

Runtime requires JDK 17, PostgreSQL 17-compatible service with pgvector, and production/staging S3-compatible object storage. Test CI uses deterministic AI/embedding/OCR/translation adapters, real PostgreSQL, real S3-compatible MinIO, real Ktor and a real Android emulator. Production AI credentials stay server-side; no provider key belongs in the APK. Android debug/test uses emulator host mapping; production configuration must use HTTPS.
