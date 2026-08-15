#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/screens evidence/motion

APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"

test -s "$APK"
test -s "$TEST_APK"

adb reverse tcp:8080 tcp:8080
adb install -r "$APK"
adb install -r "$TEST_APK"

I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"

adb shell am instrument -w -e class com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest "$I" > evidence/session-seed-test.txt
cat evidence/session-seed-test.txt
grep -q 'OK (1 test)' evidence/session-seed-test.txt

adb shell am instrument -w -e class com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest "$I" > evidence/frontend-ui-tests.txt
cat evidence/frontend-ui-tests.txt
grep -q 'OK (2 tests)' evidence/frontend-ui-tests.txt

adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/shell-ui-test.txt
cat evidence/shell-ui-test.txt
grep -q 'OK (1 test)' evidence/shell-ui-test.txt

adb shell am force-stop com.veltrix.hom.vnext.dev
adb shell monkey -p com.veltrix.hom.vnext.dev 1 >/dev/null
sleep 4
adb exec-out screencap -p > evidence/screens/home.png

adb shell dumpsys gfxinfo com.veltrix.hom.vnext.dev reset || true
adb shell screenrecord --time-limit 6 /sdcard/motion.mp4 >/dev/null 2>&1 &
P=$!
sleep 1
adb shell input tap 405 1770
sleep 2
adb shell input tap 90 140
sleep 2
wait "$P" || true

adb pull /sdcard/motion.mp4 evidence/motion/navigation-motion.mp4 >/dev/null
adb shell dumpsys gfxinfo com.veltrix.hom.vnext.dev framestats > evidence/gfxinfo-framestats.txt || true

test -s evidence/screens/home.png
test -s evidence/motion/navigation-motion.mp4

echo FRONTEND_PART1_RUNTIME=PASS | tee evidence/runtime-gate.txt
