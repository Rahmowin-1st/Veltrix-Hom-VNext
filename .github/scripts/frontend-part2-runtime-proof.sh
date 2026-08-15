#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics
APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"

test -s "$APK"
test -s "$TEST_APK"
adb reverse tcp:8080 tcp:8080
adb install -r "$APK"
adb install -r "$TEST_APK"
I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"

run_test() {
  local cls="$1" out="$2" expect="${3:-OK}"
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb shell am instrument -w -e class "$cls" "$I" > "$out"
  cat "$out"
  grep -q "$expect" "$out"
}

# Accepted foundation + new Part 2 real-backend contracts.
run_test com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest evidence/runtime/server-foundation.txt 'OK (1 test)'
run_test com.veltrix.hom.vnext.Part2ServerIntegrationInstrumentedTest evidence/runtime/part2-server-contracts.txt 'OK (1 test)'

# Pure Compose acceptance for the old verified core and new worlds.
run_test com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest evidence/runtime/part1-ui-regression.txt 'OK (2 tests)'
run_test com.veltrix.hom.vnext.FrontendPart2UiInstrumentedTest evidence/runtime/part2-ui-worlds.txt 'OK (6 tests)'
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/runtime/shell.txt 'OK (1 test)'

# MainActivity must render a loaded Part 2 Home from the real session seeded above.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" > evidence/runtime/main-start.txt
for _ in $(seq 1 35); do
  adb shell uiautomator dump /sdcard/part2-main.xml >/dev/null 2>&1 || true
  adb pull /sdcard/part2-main.xml evidence/runtime/main-ui.xml >/dev/null 2>&1 || true
  if grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml 2>/dev/null; then break; fi
  sleep .5
done
grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml
! grep -q '{&quot;id&quot;' evidence/runtime/main-ui.xml
adb exec-out screencap -p > evidence/screens/live-home.png
test -s evidence/screens/live-home.png

# A11Y: extreme text, reduced motion, TalkBack presence/semantics.
adb shell settings put system font_scale 2.0
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/accessibility/extreme-font-shell.txt 'OK (1 test)'
adb shell settings put system font_scale 1.0
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/accessibility/reduced-motion-shell.txt 'OK (1 test)'
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1

adb shell pm list packages | grep -Ei 'talkback|accessibility' | tee evidence/accessibility/accessibility-packages.txt || true
if adb shell pm path com.google.android.marvin.talkback >/dev/null 2>&1; then
  TB='com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
  adb shell settings put secure enabled_accessibility_services "$TB"
  adb shell settings put secure accessibility_enabled 1
  adb shell settings put secure touch_exploration_enabled 1
  sleep 1
  adb shell dumpsys accessibility > evidence/accessibility/talkback-dumpsys.txt || true
  grep -qi talkback evidence/accessibility/talkback-dumpsys.txt
  run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/accessibility/talkback-shell.txt 'OK (1 test)'
  echo TALKBACK_RUNTIME=PASS | tee evidence/accessibility/talkback-gate.txt
else
  echo TALKBACK_RUNTIME=NOT_AVAILABLE_IN_GOOGLE_APIS_IMAGE | tee evidence/accessibility/talkback-gate.txt
fi
adb shell settings put secure touch_exploration_enabled 0 >/dev/null 2>&1 || true
adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true

# Existing local-first/offline/process-death guarantees remain regression gates.
run_test com.veltrix.hom.vnext.DurabilityInstrumentedTest evidence/runtime/durability.txt 'OK (2 tests)'
run_test com.veltrix.hom.vnext.OfflineDataInstrumentedTest evidence/runtime/offline.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#aSeedPart3State' evidence/runtime/process-death-seed.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#zVerifyPart3StateAfterFreshInstrumentationProcess' evidence/runtime/process-death-verify.txt 'OK (1 test)'

# Environment fingerprint comes before any PF interpretation.
{
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "hardware=$(adb shell getprop ro.hardware | tr -d '\r')"
  echo "egl=$(adb shell getprop ro.hardware.egl | tr -d '\r')"
  echo "refresh=$(adb shell dumpsys display | grep -E 'mRefreshRate|fps=' | head -8 || true)"
  adb shell dumpsys SurfaceFlinger 2>/dev/null | grep -Ei 'GLES|OpenGL|Vulkan|renderer|vendor' | head -50 || true
} | tee evidence/performance/renderer-environment.txt

adb shell am force-stop "$PACKAGE"
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
sleep 2
adb shell input tap 405 2240 >/dev/null 2>&1 || true
sleep .6
adb shell input tap 135 2240 >/dev/null 2>&1 || true
sleep .6
adb shell input swipe 2 1000 800 1000 650 >/dev/null 2>&1 || true
sleep .6
adb shell input swipe 800 1000 2 1000 650 >/dev/null 2>&1 || true
adb shell dumpsys gfxinfo "$PACKAGE" framestats > evidence/performance/gfxinfo-smoke.txt || true
adb shell dumpsys meminfo "$PACKAGE" > evidence/performance/meminfo.txt || true
adb shell logcat -d -t 1000 > evidence/runtime/logcat.txt || true
! grep -q 'ANR in com.veltrix.hom.vnext' evidence/runtime/logcat.txt
! grep -q 'FATAL EXCEPTION: main' evidence/runtime/logcat.txt

echo PART1_REGRESSION_RUNTIME=PASS | tee evidence/runtime/part1-regression-gate.txt
echo PART2_BACKEND_CONTRACT_RUNTIME=PASS | tee evidence/runtime/part2-contract-gate.txt
echo PART2_A11Y_RUNTIME=PASS | tee evidence/accessibility/a11y-gate.txt
echo PART2_DURABILITY_OFFLINE_PROCESS_DEATH=PASS | tee evidence/runtime/durability-gate.txt
echo PART2_RUNTIME=PASS | tee evidence/runtime/runtime-gate.txt
