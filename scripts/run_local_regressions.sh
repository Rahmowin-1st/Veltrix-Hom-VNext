#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p out/regression out/foundation-regression out/regression-evidence
kotlinc core/src/main/kotlin/com/veltrix/hom/vnext/core/Models.kt core/src/main/kotlin/com/veltrix/hom/vnext/core/Engines.kt scripts/CoreRegression.kt -include-runtime -d out/regression/core-regression.jar
java -jar out/regression/core-regression.jar | tee out/regression-evidence/core-regression-output.txt
kotlinc core/src/main/kotlin/com/veltrix/hom/vnext/core/Models.kt core/src/main/kotlin/com/veltrix/hom/vnext/core/Engines.kt core/src/main/kotlin/com/veltrix/hom/vnext/core/Part1Systems.kt server/src/main/kotlin/com/veltrix/hom/vnext/server/foundation/SecurityPrimitives.kt server/src/main/kotlin/com/veltrix/hom/vnext/server/foundation/AiFoundation.kt server/src/main/kotlin/com/veltrix/hom/vnext/server/foundation/SourceFoundation.kt server/src/main/kotlin/com/veltrix/hom/vnext/server/RateLimiter.kt scripts/FoundationRegression.kt -include-runtime -d out/foundation-regression/foundation-regression.jar
java -jar out/foundation-regression/foundation-regression.jar | tee out/regression-evidence/foundation-regression-output.txt
grep -q 'SUMMARY | PASS=48 FAIL=0 TOTAL=48' out/regression-evidence/core-regression-output.txt
grep -q 'SUMMARY | PASS=57 FAIL=0 TOTAL=57' out/regression-evidence/foundation-regression-output.txt
echo 'LOCAL_REGRESSION_GATE=PASS total=105'
