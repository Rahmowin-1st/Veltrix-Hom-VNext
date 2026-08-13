# Deployment Runbook

Part 1 deployment boundary is the backend/app-foundation, not a production frontend release.

Server requirements: JDK 17 runtime; PostgreSQL 17-compatible database with pgvector; S3-compatible object storage; HTTPS in production; provider credentials injected through server-side secret/environment configuration only. Run migrations before serving traffic. Start Ktor, then require `/health` and `/ready` to pass before routing traffic.

Android configuration must point at the intended HTTPS backend for production. No privileged AI/storage/translation credential may be packaged in the APK. Release input changes reopen affected compile, integration, runtime and provenance gates.

Before promotion verify the exact commit through CI, migrations, backend integration/security gates, Android build/install/runtime, offline/restart/reconnect behavior, then retain the exact artifact SHA-256 and provenance record. Store/economy remains outside Part 1.