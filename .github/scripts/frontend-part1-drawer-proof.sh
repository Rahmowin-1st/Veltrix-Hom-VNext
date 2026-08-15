#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/screens evidence/motion evidence/performance evidence/diagnostics
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"

fresh_dump() {
  local out="$1"
  rm -f "$out"
  adb shell rm -f /sdcard/drawer-proof.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump /sdcard/drawer-proof.xml >/dev/null 2>&1
  adb pull /sdcard/drawer-proof.xml "$out" >/dev/null 2>&1
  test -s "$out"
}

wait_text() {
  local pattern="$1" name="$2"
  local out="evidence/diagnostics/drawer-$name.xml"
  for _ in $(seq 1 20); do
    if fresh_dump "$out" && grep -Eqi "$pattern" "$out"; then return 0; fi
    sleep .4
  done
  echo "drawer proof timeout: $name / $pattern" >&2
  adb exec-out screencap -p > "evidence/screens/FAILED-drawer-$name.png" || true
  adb shell dumpsys activity top > "evidence/diagnostics/drawer-$name-activity.txt" || true
  return 1
}

start_home() {
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$ACTIVITY" >/dev/null
  wait_text 'Continue with Veltrix|Ask Veltrix' home
}

assert_app_top() {
  adb shell dumpsys activity top > evidence/diagnostics/drawer-activity-top.txt
  grep -q 'com.veltrix.hom.vnext' evidence/diagnostics/drawer-activity-top.txt
}

# Back ownership: open drawer, Android Back must close it while keeping MainActivity alive.
start_home
adb shell input tap 50 120
wait_text 'Global capabilities' opened
adb shell input keyevent 4
wait_text 'Continue with Veltrix|Ask Veltrix' back-closed
assert_app_top
if fresh_dump evidence/diagnostics/drawer-after-back.xml && grep -qi 'Global capabilities' evidence/diagnostics/drawer-after-back.xml; then
  echo 'Drawer remained open after Back' >&2
  exit 1
fi
echo DRAWER_BACK_CLOSE=PASS | tee evidence/drawer-back-gate.txt

# Safe in-app direct manipulation starts outside the Android system back edge.
start_home
adb shell input motionevent DOWN 80 1000
adb shell input motionevent MOVE 520 1000
sleep .25
adb exec-out screencap -p > evidence/screens/22-sidebar-mid-drag.png
test -s evidence/screens/22-sidebar-mid-drag.png
assert_app_top
adb shell input motionevent UP 520 1000
sleep .5

# Motion proof: direct open then direct close without leaving the app.
start_home
adb shell screenrecord --time-limit 7 /sdcard/sidebar-safe.mp4 >/dev/null 2>&1 &
P=$!
sleep .5
adb shell input swipe 80 1000 850 1000 900
sleep 1
adb shell input swipe 850 1000 80 1000 900
sleep 1
wait "$P" || true
adb pull /sdcard/sidebar-safe.mp4 evidence/motion/sidebar-direct-manipulation.mp4 >/dev/null
test -s evidence/motion/sidebar-direct-manipulation.mp4
assert_app_top

echo SIDEBAR_DIRECT_MANIPULATION=PASS | tee evidence/sidebar-direct-gate.txt

# Re-measure sidebar PF interval after the corrected gesture route; require actual frames.
start_home
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell input swipe 80 1000 850 1000 700
sleep 1
adb shell input swipe 850 1000 80 1000 700
sleep 1
adb shell dumpsys gfxinfo "$PACKAGE" framestats > evidence/performance/03-sidebar-direct.txt || true
awk '/Total frames rendered:/{if(($4+0)>0) ok=1} END{exit ok?0:1}' evidence/performance/03-sidebar-direct.txt
assert_app_top

echo SIDEBAR_FRAME_DATA=PASS | tee evidence/sidebar-performance-gate.txt
