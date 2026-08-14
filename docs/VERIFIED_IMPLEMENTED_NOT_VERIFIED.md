# VERIFIED / IMPLEMENTED / NOT VERIFIED Classification

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

## VERIFIED when final CI is green
Deterministic progression/reward/anti-farming; PostgreSQL Part 2 schema V005/V006; XP/Coin ledgers and reconciliation; Store purchase/refund; concurrency no-overspend; consistency; achievements; inventory/avatars; avatar restart/relogin persistence; Personal Map gate/content fallback/unit rules; season rollover lifetime preservation; statistics/events; Part 2 account export keys and delete/relogin revocation; generated OpenAPI drift; current Ktor HTTP smoke; package/provenance/hash integrity.

## VERIFIED by preserved unchanged Android evidence
Android install/cold launch; full 15 connected instrumentation tests; Part2 client↔real Ktor Store/Map smoke; revision-aware cache reopen; offline/process-death boundaries. Preservation is valid only if final CI proves no diff in Android/core/build inputs from runtime SHA `2e70908...` and verifies copied APK/test APK hashes.

## IMPLEMENTED BUT NOT PRODUCTION-VERIFIED
Live production external AI/OCR/translation provider credentials/services and production-scale observability/load behavior.

## NOT VERIFIED / NOT CLAIMED
Physical-device-only behavior, production SLO certification, destructive post-retention account purge scheduler, deterministic checked-in Room schema JSON export, and Part 3 final visual design.
