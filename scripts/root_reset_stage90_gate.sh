#!/usr/bin/env bash
set -euo pipefail

OUT="evidence/stage90"
mkdir -p "$OUT/screens" "$OUT/tests"
cleanup(){ adb reverse --remove-all >/dev/null 2>&1 || true; }
trap cleanup EXIT

adb reverse tcp:8080 tcp:8080
adb reverse --list | tee "$OUT/adb-reverse.txt"
grep -q 'tcp:8080 tcp:8080' "$OUT/adb-reverse.txt"

# The workflow has already built these exact APKs from the current checkout. Run instrumentation
# manually so Android Test Orchestrator/Gradle cleanup cannot uninstall the target before we export
# the proof files written into its internal files directory.
DEBUG_APK='android/app/build/outputs/apk/debug/app-debug.apk'
TEST_APK='android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
test -s "$DEBUG_APK"
test -s "$TEST_APK"

AAPT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/37.0.0/aapt"
if [ ! -x "$AAPT" ]; then
  AAPT="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}/build-tools" -type f -name aapt -perm -111 | sort -V | tail -n 1)"
fi
test -x "$AAPT"

APP_ID="$($AAPT dump badging "$DEBUG_APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
TEST_APP_ID="$($AAPT dump badging "$TEST_APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
test "$APP_ID" = 'com.veltrix.hom.vnext.dev'
test -n "$TEST_APP_ID"
printf 'app_id=%s\ntest_app_id=%s\n' "$APP_ID" "$TEST_APP_ID" | tee "$OUT/apk-package-ids.txt"
sha256sum "$DEBUG_APK" | tee "$OUT/runtime-debug-apk-sha256.txt"
sha256sum "$TEST_APK" | tee "$OUT/runtime-androidtest-apk-sha256.txt"

adb uninstall "$TEST_APP_ID" >/dev/null 2>&1 || true
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install -r -t "$DEBUG_APK" | tee "$OUT/install-target.txt"
adb install -r -t "$TEST_APK" | tee "$OUT/install-test.txt"
grep -q '^Success$' "$OUT/install-target.txt"
grep -q '^Success$' "$OUT/install-test.txt"

adb shell pm list instrumentation | tr -d '\r' | tee "$OUT/instrumentations.txt"
RUNNER="$(awk -v prefix="instrumentation:${TEST_APP_ID}/" -v target="(target=${APP_ID})" 'index($0,prefix)==1 && index($0,target)>0 { sub(/^instrumentation:/,"",$1); print $1; exit }' "$OUT/instrumentations.txt")"
test -n "$RUNNER"
printf 'runner=%s\n' "$RUNNER" | tee "$OUT/instrumentation-runner.txt"

# Never allow one silent Android instrumentation hang to consume the whole CI job. Every Stage 90
# criterion runs in its own instrumentation process with a hard timeout and a durable per-test log.
TESTS=(
  'visual|com.veltrix.hom.vnext.RootStage90InstrumentedTest#realRootVisualMatrixAndCriticalTouchTargetsAreValid|240'
  'font200|com.veltrix.hom.vnext.RootStage90InstrumentedTest#twoHundredPercentFontKeepsSignedInAndAccountFlowsReachable|180'
  'reduced-motion|com.veltrix.hom.vnext.RootStage90InstrumentedTest#reducedMotionSystemPathKeepsNavigationFunctional|180'
  'performance|com.veltrix.hom.vnext.RootStage90PerformanceInstrumentedTest#warmedCurrentRootPrimaryNavigationHasNonPathologicalFrameClassification|240'
)
: > "$OUT/instrumentation.txt"
PASS_COUNT=0
for spec in "${TESTS[@]}"; do
  IFS='|' read -r name selector seconds <<< "$spec"
  log="$OUT/tests/${name}.txt"

  # Each invocation must start from a clean process/task boundary. Previous Stage 90 tests launch the
  # real MainActivity and deliberately change global display/animation settings; leaving their target
  # or instrumentation process alive can make a subsequent ActivityScenario attach to stale task
  # state and block before launch returns. Force-stop preserves app files/data but removes that process
  # boundary ambiguity. Every test then establishes any non-default adaptation it specifically needs.
  adb shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb shell am force-stop "$TEST_APP_ID" >/dev/null 2>&1 || true
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 1.0 >/dev/null 2>&1 || true
  sleep 1

  printf 'STAGE90_TEST_ISOLATED name=%s target_force_stopped=1 test_force_stopped=1 system_scales_reset=1\n' "$name" | tee -a "$OUT/instrumentation.txt"
  printf 'STAGE90_TEST_START name=%s selector=%s timeout=%ss\n' "$name" "$selector" "$seconds" | tee -a "$OUT/instrumentation.txt"
  set +e
  timeout --signal=TERM --kill-after=10s "${seconds}s" \
    adb shell am instrument -w -r -e class "$selector" "$RUNNER" \
    | tr -d '\r' | tee "$log"
  command_exit=${PIPESTATUS[0]}
  set -e
  cat "$log" >> "$OUT/instrumentation.txt"
  if [ "$command_exit" -eq 124 ] || [ "$command_exit" -eq 137 ]; then
    printf 'STAGE90_TEST_TIMEOUT name=%s exit=%s\n' "$name" "$command_exit" | tee -a "$OUT/instrumentation.txt"
    exit 1
  fi
  test "$command_exit" -eq 0
  test "$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' "$log" || true)" = '1'
  test "$(grep -cE '^INSTRUMENTATION_STATUS_CODE: -[0-9]+$' "$log" || true)" = '0'
  grep -q '^INSTRUMENTATION_CODE: -1$' "$log"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=Process crashed' "$log"; then
    cat "$log"
    exit 1
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  printf 'STAGE90_TEST_PASS name=%s\n' "$name" | tee -a "$OUT/instrumentation.txt"
done
test "$PASS_COUNT" = '4'
printf 'ROOT_STAGE90_TESTS=PASS tests=4 failures=0 errors=0 skipped=0\n' | tee "$OUT/test-summary.txt"

# Proof lives in the still-installed debuggable target app's internal files directory. Export only
# the fixed known proof filenames through the target app uid; no broad private-data export occurs.
REMOTE_REL='files/stage90'
FILES=(
  home.png
  personal.png
  store.png
  projects.png
  project-workspace.png
  font200-home.png
  font200-auth.png
  reduced-motion-home.png
  visual-a11y-report.txt
  font200-report.txt
  reduced-motion-report.txt
  jankstats-root.txt
)

adb shell run-as "$APP_ID" test -d "$REMOTE_REL"
for name in "${FILES[@]}"; do
  adb shell run-as "$APP_ID" test -s "$REMOTE_REL/$name"
  adb exec-out run-as "$APP_ID" cat "$REMOTE_REL/$name" > "$OUT/screens/$name"
  test -s "$OUT/screens/$name"
done
printf 'transport=MANUAL_INSTRUMENTATION_RUN_AS_CAT_INTERNAL_FILES\napp_id=%s\nremote=%s\nfiles=%s\n' \
  "$APP_ID" "$REMOTE_REL" "${#FILES[@]}" | tee "$OUT/proof-pull.txt"

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
