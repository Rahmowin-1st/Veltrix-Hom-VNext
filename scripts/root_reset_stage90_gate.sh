#!/usr/bin/env bash
set -euo pipefail

OUT="evidence/stage90"
mkdir -p "$OUT/screens"
cleanup(){ adb reverse --remove-all >/dev/null 2>&1 || true; }
trap cleanup EXIT

adb reverse tcp:8080 tcp:8080
adb reverse --list | tee "$OUT/adb-reverse.txt"
grep -q 'tcp:8080 tcp:8080' "$OUT/adb-reverse.txt"

STAGE90_CLASSES='com.veltrix.hom.vnext.RootStage90InstrumentedTest,com.veltrix.hom.vnext.RootStage90PerformanceInstrumentedTest'
gradle --no-daemon \
  -PVELTRIX_API_BASE_URL=http://127.0.0.1:8080 \
  -Pandroid.testInstrumentationRunnerArguments.class="$STAGE90_CLASSES" \
  :android:app:connectedDebugAndroidTest > "$OUT/instrumentation.txt" 2>&1 || {
    cat "$OUT/instrumentation.txt"
    exit 1
  }
cat "$OUT/instrumentation.txt"
grep -q 'BUILD SUCCESSFUL' "$OUT/instrumentation.txt"

python3 - <<'PY' | tee "$OUT/test-summary.txt"
import glob, xml.etree.ElementTree as ET
files=glob.glob('android/app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True)
tests=failures=errors=skipped=0
matched=[]
classes={'RootStage90InstrumentedTest','RootStage90PerformanceInstrumentedTest'}
for path in files:
    try: root=ET.parse(path).getroot()
    except ET.ParseError: continue
    cases=[]
    for c in root.iter('testcase'):
        classname=c.attrib.get('classname','')
        if any(name in classname for name in classes): cases.append(c)
    if not cases: continue
    matched.append(path)
    tests += len(cases)
    failures += sum(1 for c in cases if c.find('failure') is not None)
    errors += sum(1 for c in cases if c.find('error') is not None)
    skipped += sum(1 for c in cases if c.find('skipped') is not None)
if tests != 4 or failures or errors or skipped:
    raise SystemExit(f'ROOT_STAGE90_TESTS=FAIL tests={tests} failures={failures} errors={errors} skipped={skipped} files={matched}')
print('ROOT_STAGE90_TESTS=PASS tests=4 failures=0 errors=0 skipped=0')
PY
grep -qx 'ROOT_STAGE90_TESTS=PASS tests=4 failures=0 errors=0 skipped=0' "$OUT/test-summary.txt"

REMOTE='/sdcard/Android/data/com.veltrix.hom.vnext.dev/files/stage90'
adb shell test -d "$REMOTE"
adb pull "$REMOTE/." "$OUT/screens/" | tee "$OUT/adb-pull.txt"

for report in visual-a11y-report.txt font200-report.txt reduced-motion-report.txt jankstats-root.txt; do
  test -s "$OUT/screens/$report"
done
grep -q '^ROOT_STAGE90_VISUAL_MATRIX=PASS screens=5$' "$OUT/screens/visual-a11y-report.txt"
grep -q '^CRITICAL_TOUCH_TARGETS=PASS min_dp=48$' "$OUT/screens/visual-a11y-report.txt"
grep -q '^FONT_SCALE_200=PASS$' "$OUT/screens/font200-report.txt"
grep -q '^REDUCED_MOTION_PATH=PASS$' "$OUT/screens/reduced-motion-report.txt"
grep -q '^ROOT_STAGE90_JANKSTATS=PASS$' "$OUT/screens/jankstats-root.txt"
grep -q '^measurement=RAW_SHELL_INPUT_REAL_CHOREOGRAPHER$' "$OUT/screens/jankstats-root.txt"
grep -q '^RELEASE_PROFILEABLE_PF=NOT_VERIFIED$' "$OUT/screens/jankstats-root.txt"
grep -q '^PHYSICAL_DEVICE_PF=NOT_VERIFIED$' "$OUT/screens/jankstats-root.txt"

PNG_COUNT="$(find "$OUT/screens" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
test "$PNG_COUNT" = '8'
find "$OUT/screens" -maxdepth 1 -type f -name '*.png' -size +20000c | sort > "$OUT/png-files.txt"
test "$(wc -l < "$OUT/png-files.txt" | tr -d ' ')" = '8'

python3 - "$OUT/screens" <<'PY' | tee "$OUT/png-dimensions.txt"
import pathlib, struct, sys
root=pathlib.Path(sys.argv[1])
files=sorted(root.glob('*.png'))
if len(files) != 8:
    raise SystemExit(f'PNG_DIMENSIONS=FAIL count={len(files)}')
for p in files:
    data=p.read_bytes()[:24]
    if data[:8] != b'\x89PNG\r\n\x1a\n' or len(data) < 24:
        raise SystemExit(f'PNG_DIMENSIONS=FAIL invalid={p.name}')
    w,h=struct.unpack('>II', data[16:24])
    if w < 720 or h < 1280:
        raise SystemExit(f'PNG_DIMENSIONS=FAIL small={p.name} {w}x{h}')
    print(f'{p.name} {w}x{h}')
print('PNG_DIMENSIONS=PASS count=8')
PY
grep -q '^PNG_DIMENSIONS=PASS count=8$' "$OUT/png-dimensions.txt"

sha256sum "$OUT"/screens/*.png | sort | tee "$OUT/screenshot-sha256.txt"
sha256sum "$OUT"/screens/*.txt | sort | tee "$OUT/report-sha256.txt"

cat > "$OUT/stage90-gate.txt" <<'EOF'
CURRENT_ROOT_VISUAL_PROOF=PASS
CORE_SCREEN_MATRIX=PASS
CRITICAL_TOUCH_TARGETS=PASS
FONT_SCALE_200=PASS
REDUCED_MOTION_PATH=PASS
API36_JANKSTATS_SANITY=PASS
RELEASE_PROFILEABLE_PF=NOT_VERIFIED
PHYSICAL_DEVICE_PF=NOT_VERIFIED
EOF
cat "$OUT/stage90-gate.txt"
