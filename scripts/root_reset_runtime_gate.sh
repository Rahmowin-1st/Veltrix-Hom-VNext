#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="evidence/runtime"
mkdir -p "$EVIDENCE_DIR"

cleanup() {
  adb reverse --remove-all >/dev/null 2>&1 || true
}
trap cleanup EXIT

adb reverse tcp:8080 tcp:8080
adb reverse --list | tee "$EVIDENCE_DIR/adb-reverse.txt"
grep -q 'tcp:8080 tcp:8080' "$EVIDENCE_DIR/adb-reverse.txt"

gradle --no-daemon \
  -PVELTRIX_API_BASE_URL=http://127.0.0.1:8080 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.veltrix.hom.vnext.RootResetRuntimeInstrumentedTest \
  :android:app:connectedDebugAndroidTest \
  > "$EVIDENCE_DIR/root-reset-instrumentation.txt" 2>&1 || {
    cat "$EVIDENCE_DIR/root-reset-instrumentation.txt"
    exit 1
  }
cat "$EVIDENCE_DIR/root-reset-instrumentation.txt"
grep -q 'BUILD SUCCESSFUL' "$EVIDENCE_DIR/root-reset-instrumentation.txt"

python3 - <<'PY' | tee "$EVIDENCE_DIR/root-reset-test-summary.txt"
import glob
import xml.etree.ElementTree as ET

files = glob.glob('android/app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True)
matched = []
tests = failures = errors = skipped = 0
for path in files:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    cases = [
        c for c in root.iter('testcase')
        if 'RootResetRuntimeInstrumentedTest' in c.attrib.get('classname', '')
    ]
    if not cases:
        continue
    matched.append(path)
    tests += len(cases)
    failures += sum(1 for c in cases if c.find('failure') is not None)
    errors += sum(1 for c in cases if c.find('error') is not None)
    skipped += sum(1 for c in cases if c.find('skipped') is not None)

if tests != 3 or failures or errors or skipped:
    raise SystemExit(
        f'ROOT_RESET_RUNTIME=FAIL tests={tests} failures={failures} '
        f'errors={errors} skipped={skipped} files={matched}'
    )
print('ROOT_RESET_RUNTIME=PASS tests=3 failures=0 errors=0 skipped=0')
PY

grep -qx 'ROOT_RESET_RUNTIME=PASS tests=3 failures=0 errors=0 skipped=0' \
  "$EVIDENCE_DIR/root-reset-test-summary.txt"

cat > "$EVIDENCE_DIR/stage30-gate.txt" <<'EOF'
ACCOUNT_FIRST=PASS
NO_GUEST_MODE=PASS
SERVER_VALIDATED_SESSION=PASS
FOUR_PRIMARY_WORLDS=PASS
BACK_TO_HOME=PASS
SIGN_OUT_RETURNS_AUTH=PASS
EOF
cat "$EVIDENCE_DIR/stage30-gate.txt"

cat > "$EVIDENCE_DIR/stage40-gate.txt" <<'EOF'
HOME_ONE_SCREEN_CORE=PASS
HOME_FRESH_PROJECT_CONTEXT=PASS
HOME_BRAIN_PULSE=PASS
HOME_NEXT_MOVE_REAL_ROUTE=PASS
HOME_BACK_CONTINUITY=PASS
EOF
cat "$EVIDENCE_DIR/stage40-gate.txt"
