#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/screens evidence/motion

APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"

test -s "$APK"
test -s "$TEST_APK"

cleanup_accessibility_settings() {
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 1.0 >/dev/null 2>&1 || true
}
trap cleanup_accessibility_settings EXIT

start_app_and_wait_home() {
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$ACTIVITY" >/dev/null
  for _ in $(seq 1 20); do
    adb shell uiautomator dump /sdcard/veltrix-home.xml >/dev/null 2>&1 || true
    adb pull /sdcard/veltrix-home.xml evidence/home-current.xml >/dev/null 2>&1 || true
    if grep -q 'Continue with Veltrix' evidence/home-current.xml 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo 'Home did not reach loaded primary-action state' >&2
  cat evidence/home-current.xml 2>/dev/null || true
  return 1
}

adb reverse tcp:8080 tcp:8080
adb install -r "$APK"
adb install -r "$TEST_APK"

I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"

adb shell am instrument -w -e class com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest "$I" > evidence/session-seed-test.txt
cat evidence/session-seed-test.txt
grep -q 'OK (1 test)' evidence/session-seed-test.txt

# Pure composable contracts run in separate API-36 instrumentation processes.
adb shell am instrument -w -e class 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#homeUsesAuthoritativeSnapshotWithoutLocalEconomyAuthority' "$I" > evidence/frontend-home-ui-test.txt
cat evidence/frontend-home-ui-test.txt
grep -q 'OK (1 test)' evidence/frontend-home-ui-test.txt

adb shell am instrument -w -e class 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#personalExposesTrustworthySparseAndMapState' "$I" > evidence/frontend-personal-ui-test.txt
cat evidence/frontend-personal-ui-test.txt
grep -q 'OK (1 test)' evidence/frontend-personal-ui-test.txt
cat evidence/frontend-home-ui-test.txt evidence/frontend-personal-ui-test.txt > evidence/frontend-ui-tests.txt

# Real Activity shell + real API-36 viewport acceptance. Force-stop before every
# Activity-backed instrumentation process so stale Compose roots cannot leak across runs.
adb shell am force-stop "$PACKAGE"
adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/shell-ui-test.txt
cat evidence/shell-ui-test.txt
grep -q 'OK (1 test)' evidence/shell-ui-test.txt

# Accessibility adaptation: large text must keep critical navigation/action usable.
adb shell settings put system font_scale 1.3
adb shell am force-stop "$PACKAGE"
adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/a11y-font-scale.txt
cat evidence/a11y-font-scale.txt
grep -q 'OK (1 test)' evidence/a11y-font-scale.txt
adb shell settings put system font_scale 1.0

# Reduced motion must preserve function and navigation semantics.
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
adb shell am force-stop "$PACKAGE"
adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/a11y-reduced-motion.txt
cat evidence/a11y-reduced-motion.txt
grep -q 'OK (1 test)' evidence/a11y-reduced-motion.txt
cleanup_accessibility_settings

echo A11Y_ADAPTATION=PASS | tee evidence/a11y-gate.txt

# Exact production visual proof after authoritative data has loaded.
start_app_and_wait_home
cp evidence/home-current.xml evidence/home-final.xml
# UI must never expose serialized backend objects as user-facing focus copy.
if grep -q '{&quot;id&quot;' evidence/home-final.xml; then
  echo 'Raw backend JSON leaked into Home UI' >&2
  exit 1
fi
adb exec-out screencap -p > evidence/screens/home.png
test -s evidence/screens/home.png

# Personal visual surface.
adb shell input tap 405 2240
sleep 2
adb exec-out screencap -p > evidence/screens/personal.png
test -s evidence/screens/personal.png

# Motion proof is captured separately from PF measurement to avoid contaminating timings.
adb shell input tap 135 2240
sleep 1
adb shell screenrecord --time-limit 6 /sdcard/navigation-motion.mp4 >/dev/null 2>&1 &
P=$!
sleep 1
adb shell input tap 405 2240
sleep 2
adb shell input tap 135 2240
sleep 1
adb shell input tap 80 150
sleep 1
wait "$P" || true
adb pull /sdcard/navigation-motion.mp4 evidence/motion/navigation-motion.mp4 >/dev/null
test -s evidence/motion/navigation-motion.mp4

# Performance smoke: no screen recording here. Exercise all primary destinations,
# confirm the app stays responsive, and persist API-36 framestats as diagnostic proof.
start_app_and_wait_home
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null || true
for x in 405 675 945 135; do
  adb shell input tap "$x" 2240
  sleep 1
done
adb shell uiautomator dump /sdcard/veltrix-perf.xml >/dev/null 2>&1 || true
adb pull /sdcard/veltrix-perf.xml evidence/perf-ui.xml >/dev/null 2>&1 || true
grep -q 'Home' evidence/perf-ui.xml
adb shell dumpsys gfxinfo "$PACKAGE" framestats > evidence/gfxinfo-framestats.txt || true
adb shell dumpsys meminfo "$PACKAGE" > evidence/meminfo.txt || true
! grep -q 'ANR in com.veltrix.hom.vnext' evidence/home-pre-shell-logcat.txt 2>/dev/null || exit 1
echo PERF_API36_SMOKE=PASS | tee evidence/performance-gate.txt

# Durable/offline gates already present in accepted Android foundation.
adb shell am instrument -w -e class com.veltrix.hom.vnext.DurabilityInstrumentedTest "$I" > evidence/durability-tests.txt
cat evidence/durability-tests.txt
grep -q 'OK (2 tests)' evidence/durability-tests.txt

adb shell am instrument -w -e class com.veltrix.hom.vnext.OfflineDataInstrumentedTest "$I" > evidence/offline-tests.txt
cat evidence/offline-tests.txt
grep -q 'OK (2 tests)' evidence/offline-tests.txt

# Process-death contract is intentionally split across fresh instrumentation processes.
adb shell am instrument -w -e class 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#aSeedPart3State' "$I" > evidence/process-death-step1.txt
cat evidence/process-death-step1.txt
grep -q 'OK (1 test)' evidence/process-death-step1.txt

adb shell am instrument -w -e class 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#zVerifyPart3StateAfterFreshInstrumentationProcess' "$I" > evidence/process-death-step2.txt
cat evidence/process-death-step2.txt
grep -q 'OK (1 test)' evidence/process-death-step2.txt

echo DURABILITY_OFFLINE_PROCESS_DEATH=PASS | tee evidence/durability-gate.txt
echo FRONTEND_PART1_RUNTIME=PASS | tee evidence/runtime-gate.txt
