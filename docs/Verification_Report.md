# Verification Report

This is the durable Part 1 verification map, not a self-acceptance claim. A status is VERIFIED only when the corresponding executed evidence is tied to the exact source under test.

Current gate families:
- Core/server compile + tests + PostgreSQL: covered by the Part 1 Source Audit workflow.
- Deterministic regression inventory: 105-case regression gate in Source Audit/final workflows.
- API contract/structure: OpenAPI parse/drift and repository structure gates.
- PostgreSQL/pgvector + S3-compatible integration and security isolation: final hosted gate family.
- Android: SDK37 build, API36 stable functional runtime, install/launcher/cold launch, capability reachability, real server-device integration, Project+Goal+Note durability across process restart, offline, reconnect and full instrumentation.
- Package/provenance: exact tested source + APK + androidTest APK + evidence/docs/contracts + SHA-256.

The latest committed source may advance after a historical GREEN run; such evidence remains reusable only for unaffected gates. The final Android runtime/package gate remains OPEN until a final exact-head workflow succeeds. Do not infer GREEN from this document alone.

Canonical test sources are under `core/src/test`, `server/src/test`, and `android/app/src/androidTest`. Final run/job/artifact identifiers belong in the generated CI provenance/evidence package.