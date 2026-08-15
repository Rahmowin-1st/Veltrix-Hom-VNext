#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/screens evidence/motion evidence/performance evidence/accessibility evidence/diagnostics
PACKAGE="com.veltrix.hom.vnext.dev"
MAIN_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"
EVIDENCE_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.FrontendEvidenceActivity"
I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"

cleanup_device() {
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put secure high_text_contrast_enabled 0 >/dev/null 2>&1 || true
  adb shell settings put secure touch_exploration_enabled 0 >/dev/null 2>&1 || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
  adb shell setprop debug.force_rtl false >/dev/null 2>&1 || true
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
}
trap cleanup_device EXIT
cleanup_device
adb reverse tcp:8080 tcp:8080

{
  echo "ro.hardware=$(adb shell getprop ro.hardware | tr -d '\r')"
  echo "ro.hardware.egl=$(adb shell getprop ro.hardware.egl | tr -d '\r')"
  echo "ro.opengles.version=$(adb shell getprop ro.opengles.version | tr -d '\r')"
  adb shell dumpsys SurfaceFlinger 2>/dev/null | grep -Ei 'GLES|OpenGL|Vulkan|renderer|vendor' | head -40 || true
} | tee evidence/performance/renderer.txt

fresh_dump() {
  local local_file="$1"
  rm -f "$local_file"
  adb shell rm -f /sdcard/veltrix-proof.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump /sdcard/veltrix-proof.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/veltrix-proof.xml "$local_file" >/dev/null 2>&1 || return 1
  test -s "$local_file"
}

state_diagnostics() {
  local name="$1"
  adb exec-out screencap -p > "evidence/screens/FAILED-$name.png" 2>/dev/null || true
  adb shell dumpsys activity top > "evidence/diagnostics/$name-activity.txt" 2>/dev/null || true
  adb shell logcat -d -t 500 > "evidence/diagnostics/$name-logcat.txt" 2>/dev/null || true
}

wait_ui() {
  local pattern="$1" name="$2" attempts="${3:-20}"
  local file="evidence/diagnostics/$name-ui.xml"
  for _ in $(seq 1 "$attempts"); do
    if fresh_dump "$file" && grep -Eqi "$pattern" "$file"; then
      sleep .35
      return 0
    fi
    sleep .5
  done
  echo "UI state timeout: $name / $pattern" >&2
  cat "$file" 2>/dev/null || true
  state_diagnostics "$name"
  return 1
}

start_main_and_wait_home() {
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$MAIN_ACTIVITY" >/dev/null
  wait_ui 'Continue with Veltrix|Ask Veltrix' main-home 30
  cp evidence/diagnostics/main-home-ui.xml evidence/home-current-final.xml
  if grep -qi 'Loading your world' evidence/home-current-final.xml; then
    echo 'Loaded Home proof still contains loading-only state' >&2
    return 1
  fi
}

fixture_marker() {
  case "$1" in
    HOME_ERROR|PERSONAL_ERROR) echo 'Veltrix is temporarily unavailable' ;;
    HOME_SPARSE) echo 'Build your next learning focus|Ask Veltrix' ;;
    HOME_OFFLINE) echo 'Offline' ;;
    HOME_FOCUS|HOME_UNLOCKED) echo "Retest Newton|Continue with Veltrix" ;;
    PERSONAL_OFFLINE) echo 'Offline' ;;
    PERSONAL_*) echo 'Who you are becoming over time|PERSONAL' ;;
    *) echo 'Veltrix' ;;
  esac
}

start_fixture() {
  local scenario="$1"
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario "$scenario" >/dev/null
  wait_ui "$(fixture_marker "$scenario")" "fixture-$scenario" 30
}

capture_fixture() {
  local scenario="$1" output="$2"
  start_fixture "$scenario"
  adb exec-out screencap -p > "evidence/screens/$output"
  test -s "evidence/screens/$output"
}

node_center() {
  local needle="$1" file="evidence/diagnostics/node-ui.xml"
  for _ in $(seq 1 12); do
    if fresh_dump "$file"; then
      if python3 - "$needle" "$file" <<'PY'
import re,sys,xml.etree.ElementTree as ET
needle=sys.argv[1].strip().lower(); path=sys.argv[2]
try: root=ET.parse(path).getroot()
except Exception: sys.exit(1)
def center(n):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m:return None
    x1,y1,x2,y2=map(int,m.groups()); return ((x1+x2)//2,(y1+y2)//2)
for exact in (True,False):
    for n in root.iter('node'):
        vals=[n.attrib.get('text','').strip().lower(), n.attrib.get('content-desc','').strip().lower()]
        ok=any(v==needle for v in vals) if exact else any(needle in v for v in vals)
        c=center(n)
        if ok and c:
            print(*c); sys.exit(0)
sys.exit(1)
PY
      then return 0; fi
    fi
    sleep .4
  done
  return 1
}

tap_text() { local c; c="$(node_center "$1")"; test -n "$c"; adb shell input tap $c; }
press_text() { local c; c="$(node_center "$1")"; test -n "$c"; set -- $c; adb shell input swipe "$1" "$2" "$1" "$2" 650; }

# A11Y runtime gates.
adb shell settings put system font_scale 2.0
adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/accessibility/extreme-font-shell.txt
cat evidence/accessibility/extreme-font-shell.txt
grep -q 'OK (1 test)' evidence/accessibility/extreme-font-shell.txt
adb shell settings put system font_scale 1.0

adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/accessibility/reduced-motion-shell-final.txt
cat evidence/accessibility/reduced-motion-shell-final.txt
grep -q 'OK (1 test)' evidence/accessibility/reduced-motion-shell-final.txt
cleanup_device
adb reverse tcp:8080 tcp:8080

adb shell pm list packages | grep -Ei 'talkback|accessibility' | tee evidence/accessibility/accessibility-packages.txt || true
if adb shell pm path com.google.android.marvin.talkback >/dev/null 2>&1; then
  TB='com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
  adb shell settings put secure enabled_accessibility_services "$TB"
  adb shell settings put secure accessibility_enabled 1
  adb shell settings put secure touch_exploration_enabled 1
  sleep 2
  adb shell dumpsys accessibility > evidence/accessibility/talkback-dumpsys.txt || true
  grep -qi 'talkback' evidence/accessibility/talkback-dumpsys.txt
  adb shell am instrument -w -e class com.veltrix.hom.vnext.ShellInstrumentedTest "$I" > evidence/accessibility/talkback-shell.txt
  cat evidence/accessibility/talkback-shell.txt
  grep -q 'OK (1 test)' evidence/accessibility/talkback-shell.txt
  echo 'TALKBACK_RUNTIME=PASS' | tee evidence/accessibility/talkback-gate.txt
else
  echo 'TALKBACK_RUNTIME=NOT_AVAILABLE_IN_GOOGLE_APIS_IMAGE' | tee evidence/accessibility/talkback-gate.txt
fi
cleanup_device
adb reverse tcp:8080 tcp:8080

# Visual matrix: live server state plus debug-only repository-level fixture states.
start_main_and_wait_home
if grep -q '{&quot;id&quot;' evidence/home-current-final.xml; then echo 'Raw backend JSON leaked into Home UI' >&2; exit 1; fi
adb exec-out screencap -p > evidence/screens/01-home-standard-loaded.png
capture_fixture HOME_FOCUS 02-home-focus-map-locked.png
capture_fixture HOME_SPARSE 04-home-sparse.png
capture_fixture HOME_OFFLINE 05-home-offline-cached.png
capture_fixture HOME_ERROR 06-home-error-no-cache.png
capture_fixture HOME_UNLOCKED 06b-home-map-active-fixture.png

adb shell wm size 900x2000
adb shell wm density 420
capture_fixture HOME_FOCUS 07-home-narrow.png
adb shell wm size reset
adb shell wm density reset
cp evidence/screens/01-home-standard-loaded.png evidence/screens/08-home-standard-phone.png

adb shell wm size 1600x2560
adb shell wm density 240
start_main_and_wait_home
adb exec-out screencap -p > evidence/screens/09-home-expanded.png
tap_text 'Personal'
wait_ui 'Who you are becoming over time|PERSONAL' expanded-personal 30
adb exec-out screencap -p > evidence/screens/19-personal-expanded.png
adb shell wm size reset
adb shell wm density reset

capture_fixture PERSONAL_STANDARD 10-personal-overview-identity.png
adb shell input swipe 540 1780 540 720 350
sleep .5
adb exec-out screencap -p > evidence/screens/12-personal-intelligence-map-locked.png
adb shell input swipe 540 1780 540 720 350
sleep .5
adb exec-out screencap -p > evidence/screens/16-personal-achievements-growth.png
capture_fixture PERSONAL_UNLOCKED 15-personal-map-active.png
adb shell input swipe 540 1780 540 720 350
sleep .5
adb exec-out screencap -p > evidence/screens/15b-personal-map-active-detail.png
capture_fixture PERSONAL_OFFLINE 17-personal-offline.png

adb shell settings put system font_scale 2.0
capture_fixture PERSONAL_STANDARD 18-personal-extreme-font.png
adb shell settings put system font_scale 1.0
adb shell settings put secure high_text_contrast_enabled 1
capture_fixture HOME_FOCUS 24-home-high-contrast-fallback.png
adb shell settings get secure high_text_contrast_enabled > evidence/accessibility/high-contrast-setting.txt
adb shell settings put secure high_text_contrast_enabled 0
adb shell setprop debug.force_rtl true || true
capture_fixture HOME_FOCUS 25-home-rtl.png
adb shell setprop debug.force_rtl false || true

start_main_and_wait_home
adb exec-out screencap -p > evidence/screens/20a-nav-home.png
adb shell input tap 405 2240; wait_ui 'Who you are becoming over time|PERSONAL' nav-personal 20; adb exec-out screencap -p > evidence/screens/20b-nav-personal.png
adb shell input tap 675 2240; wait_ui 'Store' nav-store 20; adb exec-out screencap -p > evidence/screens/20c-nav-store.png
adb shell input tap 945 2240; wait_ui 'Projects' nav-projects 20; adb exec-out screencap -p > evidence/screens/20d-nav-projects.png

start_main_and_wait_home
adb exec-out screencap -p > evidence/screens/21a-sidebar-closed.png
tap_text 'Open Veltrix capabilities'
wait_ui 'Global capabilities' sidebar-open 20
adb exec-out screencap -p > evidence/screens/21b-sidebar-open.png
adb shell input keyevent 4
sleep .4
if adb shell input motionevent DOWN 2 1000 >/dev/null 2>&1; then
  adb shell input motionevent MOVE 520 1000 >/dev/null 2>&1 || true
  sleep .2
  adb exec-out screencap -p > evidence/screens/22-sidebar-mid-drag.png || true
  adb shell input motionevent UP 520 1000 >/dev/null 2>&1 || true
  echo 'SIDEBAR_MID_DRAG=CAPTURED' > evidence/sidebar-mid-drag.txt
else
  echo 'SIDEBAR_MID_DRAG=NOT_SUPPORTED_BY_SHELL_INPUT' > evidence/sidebar-mid-drag.txt
fi

cat > evidence/visual-evidence-matrix.txt <<'MATRIX'
HOME
01 standard loaded = screens/01-home-standard-loaded.png
02 focus/active learning = screens/02-home-focus-map-locked.png
03 Map locked = screens/02-home-focus-map-locked.png
04 sparse = screens/04-home-sparse.png
05 offline cached = screens/05-home-offline-cached.png
06 error/no-cache/retry = screens/06-home-error-no-cache.png
07 narrow phone = screens/07-home-narrow.png
08 standard phone = screens/08-home-standard-phone.png
09 expanded/tablet = screens/09-home-expanded.png
optional active Map repository fixture = screens/06b-home-map-active-fixture.png
PERSONAL
10 overview = screens/10-personal-overview-identity.png
11 identity/avatar/progression = screens/10-personal-overview-identity.png
12 Student Model/Memory = screens/12-personal-intelligence-map-locked.png
13 strengths/weaknesses/statistics = screens/12-personal-intelligence-map-locked.png
14 Map locked = screens/12-personal-intelligence-map-locked.png
15 Map active/current progression fixture = screens/15-personal-map-active.png + screens/15b-personal-map-active-detail.png
16 achievements/progression = screens/16-personal-achievements-growth.png
17 offline = screens/17-personal-offline.png
18 extreme font = screens/18-personal-extreme-font.png
19 expanded/tablet = screens/19-personal-expanded.png
GLOBAL SHELL
20 bottom nav states = screens/20a-nav-home.png .. screens/20d-nav-projects.png
21 sidebar closed/open = screens/21a-sidebar-closed.png + screens/21b-sidebar-open.png
22 sidebar mid-drag = screens/22-sidebar-mid-drag.png when shell input supports it
23 error/conflict/retry = screens/06-home-error-no-cache.png
24 high-contrast/reduced-transmission fallback = screens/24-home-high-contrast-fallback.png
additional RTL adaptation = screens/25-home-rtl.png
MATRIX

# Motion clips. Screen recording remains separate from PF intervals.
cleanup_device
adb reverse tcp:8080 tcp:8080
start_main_and_wait_home
adb shell screenrecord --time-limit 7 /sdcard/nav.mp4 >/dev/null 2>&1 & P=$!
sleep .5
adb shell input tap 405 2240; sleep 1
adb shell input tap 675 2240; sleep 1
adb shell input tap 945 2240; sleep 1
adb shell input tap 135 2240; sleep 1
wait "$P" || true
adb pull /sdcard/nav.mp4 evidence/motion/navigation-destinations.mp4 >/dev/null
test -s evidence/motion/navigation-destinations.mp4

start_main_and_wait_home
adb shell screenrecord --time-limit 7 /sdcard/sidebar.mp4 >/dev/null 2>&1 & P=$!
sleep .5
adb shell input swipe 2 1000 850 1000 900; sleep 1
adb shell input swipe 850 1000 2 1000 900; sleep 1
wait "$P" || true
adb pull /sdcard/sidebar.mp4 evidence/motion/sidebar-direct-manipulation.mp4 >/dev/null
test -s evidence/motion/sidebar-direct-manipulation.mp4

adb shell screenrecord --time-limit 7 /sdcard/home-primary.mp4 >/dev/null 2>&1 & P=$!
start_fixture HOME_FOCUS
press_text 'Continue with Veltrix'
sleep 1
wait "$P" || true
adb pull /sdcard/home-primary.mp4 evidence/motion/home-avatar-primary-glass.mp4 >/dev/null
test -s evidence/motion/home-avatar-primary-glass.mp4

start_fixture PERSONAL_UNLOCKED
adb shell screenrecord --time-limit 6 /sdcard/map.mp4 >/dev/null 2>&1 & P=$!
sleep .5
adb shell input swipe 540 1780 540 720 850; sleep 1
adb shell input swipe 540 720 540 1500 700; sleep 1
wait "$P" || true
adb pull /sdcard/map.mp4 evidence/motion/personal-map-exploration.mp4 >/dev/null
test -s evidence/motion/personal-map-exploration.mp4

adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
start_main_and_wait_home
adb shell screenrecord --time-limit 5 /sdcard/reduced.mp4 >/dev/null 2>&1 & P=$!
sleep .5
adb shell input tap 405 2240; sleep 1
adb shell input tap 135 2240; sleep 1
wait "$P" || true
adb pull /sdcard/reduced.mp4 evidence/motion/reduced-motion-navigation.mp4 >/dev/null
test -s evidence/motion/reduced-motion-navigation.mp4
cleanup_device
adb reverse tcp:8080 tcp:8080

cat > evidence/motion-evidence-matrix.txt <<'MOTION'
bottom navigation transition = motion/navigation-destinations.mp4
sidebar direct open/close = motion/sidebar-direct-manipulation.mp4
Home circular identity entrance + primary glass press/release = motion/home-avatar-primary-glass.mp4
Personal Map spatial exploration/scroll = motion/personal-map-exploration.mp4
Reduced Motion navigation = motion/reduced-motion-navigation.mp4
Home→Personal shared element = NOT IMPLEMENTED; no false shared-transition claim
major Map unlock/reward = NOT CAPTURED; no authoritative unlock mutation was executed
MOTION

# PF windows are separate from recordings/screenshots/UiAutomator.
perf_dump() { adb shell dumpsys gfxinfo "$PACKAGE" framestats > "evidence/performance/$1.txt" || true; }
adb shell am force-stop "$PACKAGE"
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario HOME_FOCUS >/dev/null
sleep 3
perf_dump 01-home-cold-avatar

start_main_and_wait_home
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell input tap 405 2240; sleep 1
adb shell input tap 135 2240; sleep 1
adb shell input tap 675 2240; sleep 1
adb shell input tap 135 2240; sleep 1
perf_dump 02-navigation-home-personal

start_main_and_wait_home
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell input swipe 2 1000 850 1000 700; sleep 1
adb shell input swipe 850 1000 2 1000 700; sleep 1
perf_dump 03-sidebar-direct

start_fixture PERSONAL_UNLOCKED
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell input swipe 540 1780 540 720 700; sleep 1
adb shell input swipe 540 720 540 1500 600; sleep 1
perf_dump 04-personal-map

adb shell wm size 1600x2560
adb shell wm density 240
adb shell am force-stop "$PACKAGE"
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
adb shell am start -W -n "$MAIN_ACTIVITY" >/dev/null
sleep 3
perf_dump 05-expanded-home
adb shell wm size reset
adb shell wm density reset

adb shell dumpsys meminfo "$PACKAGE" > evidence/meminfo-final.txt || true
adb shell logcat -d -t 1200 > evidence/runtime-logcat-final.txt || true
if grep -q 'ANR in com.veltrix.hom.vnext' evidence/runtime-logcat-final.txt; then echo 'ANR detected' >&2; exit 1; fi
{
  echo 'Performance windows: API 36 emulator; renderer fingerprint in performance/renderer.txt'
  for f in evidence/performance/0*.txt; do
    echo "--- $(basename "$f")"
    grep -E 'Total frames rendered|Janky frames:|50th percentile|90th percentile|95th percentile|99th percentile' "$f" | head -10 || true
  done
} | tee evidence/performance-summary-final.txt
for f in evidence/performance/0*.txt; do
  grep -q 'Total frames rendered:' "$f"
  ! grep -q 'Total frames rendered: 0' "$f"
done

echo PERF_FRAME_DATA=COLLECTED | tee evidence/performance-final-gate.txt
echo FINAL_EVIDENCE_GATE=PASS | tee evidence/final-evidence-gate.txt
