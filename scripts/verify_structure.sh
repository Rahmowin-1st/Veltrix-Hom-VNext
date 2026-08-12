#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
for d in android server core contracts database docs scripts .github/workflows; do test -d "$d"; done
for f in contracts/openapi.yaml database/migrations/V001__core_schema.sql database/migrations/V002__hot_query_indexes.sql; do test -s "$f"; done
grep -q 'applicationId = "com.veltrix.hom.vnext"' android/app/build.gradle.kts
grep -q 'jdbc:postgresql://' .env.example
! grep -R --include='*.gradle.kts' --include='*.kt' --include='*.xml' -n 'com\.veltrix\.hom[^.]' android core server 2>/dev/null
echo 'STRUCTURE_VERIFY=PASS'
