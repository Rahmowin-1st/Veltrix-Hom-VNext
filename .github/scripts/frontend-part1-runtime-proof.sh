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

# Run the two pure-Compose contracts in independent instrumentation processes.
# API 36 exposed lifecycle flakiness when deprecated createComposeRule hosted two
# setContent tests in one process; separate processes keep the contract strict and deterministic.
adb shell am instrument -w -e class 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#homeUsesAuthoritativeSnapshotWithoutLocalEconomyAuthority' "$I" > evidence/frontend-home-ui-test.txt
cat evidence/frontend-home-ui-test.txt
grep -q 'OK (1 test)' evidence/frontend-home-ui-test.txt

adb shell am instrument -w -e class 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#personalExposesTrustworthySparseAndMapState' "$I" > evidence/frontend-personal-ui-test.txt
cat evidence/frontend-personal-ui-test.txt
grep -q 'OK (1 test)' evidence/frontend-personal-ui-test.txt
cat evidence/frontend-home-ui-test.txt evidence/frontend-personal-ui-test.txt > evidence/frontend-ui-tests.txt

# Decisive pre-shell diagnostic: launch the exact production Activity after the
# seeded session, then preserve its visible state before instrumentation can fail.
adb shell am force-stop com.veltrix.hom.vnext.dev
adb shell monkey -p com.veltrix.hom.vnext.dev 1 >/dev/null
sleep 8
adb exec-out screencap -p > evidence/screens/home-pre-shell.png || true
adb shell uiautomator dump /sdcard/veltrix-home.xml >/dev/null 2>&1 || true
adb pull /sdcard/veltrix-home.xml evidence/home-pre-shell.xml >/dev/null 2>&1 || true
echo '--- HOME PRE-SHELL UI TREE ---'
head -c 16000 evidence/home-pre-shell.xml 2>/dev/null || true
echo
adb shell dumpsys activity top > evidence/home-pre-shell-activity.txt || true
adb shell logcat -d -t 500 > evidence/home-pre-shell-logcat.txt || true
adb shell am force-stop com.veltrix.hom.vnext.dev

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
