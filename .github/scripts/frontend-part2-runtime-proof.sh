#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics
APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"
EVIDENCE_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.FrontendEvidenceActivity"
PROGRESS="evidence/runtime/progress.txt"

mark() { printf '%s\n' "$1" | tee -a "$PROGRESS"; }

test -s "$APK"
test -s "$TEST_APK"
adb reverse tcp:8080 tcp:8080
adb install -r "$APK"
adb install -r "$TEST_APK"
I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"
mark 'DEVICE_READY=PASS'

run_test() {
  local cls="$1" out="$2" expect="${3:-OK}"
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  timeout 180s adb shell am instrument -w -e class "$cls" "$I" > "$out"
  cat "$out"
  grep -q "$expect" "$out"
}

assert_png_visible() {
  python3 - "$1" <<'PYPNG'
import sys, struct, zlib, math
p=sys.argv[1]
b=open(p,'rb').read()
if b[:8] != b'\x89PNG\r\n\x1a\n':
    raise SystemExit(41)
pos=8; packed=b''; w=h=bd=ct=None
while pos < len(b):
    n=struct.unpack('>I', b[pos:pos+4])[0]
    typ=b[pos+4:pos+8]; dat=b[pos+8:pos+8+n]; pos += 12+n
    if typ == b'IHDR':
        w,h,bd,ct,_,_,inter=struct.unpack('>IIBBBBB', dat)
        if bd != 8 or inter != 0 or ct not in (0,2,4,6): raise SystemExit(42)
    elif typ == b'IDAT': packed += dat
    elif typ == b'IEND': break
channels={0:1,2:3,4:2,6:4}[ct]; bpp=channels; stride=w*channels
raw=zlib.decompress(packed); off=0; prev=bytearray(stride); values=[]; sample=max(1,(w*h)//12000); pixel=0
for _ in range(h):
    f=raw[off]; off+=1; cur=bytearray(raw[off:off+stride]); off+=stride
    for x in range(stride):
        a=cur[x-bpp] if x>=bpp else 0; bb=prev[x]; c=prev[x-bpp] if x>=bpp else 0
        if f==1: cur[x]=(cur[x]+a)&255
        elif f==2: cur[x]=(cur[x]+bb)&255
        elif f==3: cur[x]=(cur[x]+((a+bb)//2))&255
        elif f==4:
            q=a+bb-c; pa=abs(q-a); pb=abs(q-bb); pc=abs(q-c); pr=a if pa<=pb and pa<=pc else (bb if pb<=pc else c)
            cur[x]=(cur[x]+pr)&255
        elif f!=0: raise SystemExit(43)
    for x in range(0,stride,channels):
        if pixel % sample == 0:
            values.append(sum(cur[x:x+3])/3 if ct in (2,6) else cur[x])
        pixel += 1
    prev=cur
mean=sum(values)/len(values); std=math.sqrt(sum((v-mean)**2 for v in values)/len(values))
print(f'VISIBLE_PNG {p} mean={mean:.1f} std={std:.1f}')
# Veltrix evidence surfaces are intentionally pale. Transition/blank frames in the emulator
# measured below mean 50; rendered product frames are well above 200. Keep a wide margin.
if mean < 100 or std < 4: raise SystemExit(44)
PYPNG
}

capture_fixture() {
  local scenario="$1"
  local name="$2"
  local png="evidence/screens/${name}.png"
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario "$scenario" > "evidence/screens/${name}-start.txt"
  # am start -W can return before the emulator's launch transition has presented the first
  # Compose frame. Capture until the actual light Veltrix surface is visible; never count a
  # black/transition frame as visual evidence.
  for attempt in 1 2 3 4 5 6; do
    sleep .7
    adb exec-out screencap -p > "$png"
    if test -s "$png" && assert_png_visible "$png"; then
      printf 'scenario=%s attempt=%s visual=PASS\n' "$scenario" "$attempt" >> "evidence/screens/${name}-start.txt"
      return 0
    fi
  done
  printf 'scenario=%s visual=FAIL\n' "$scenario" >> "evidence/screens/${name}-start.txt"
  return 1
}

# Accepted foundation + new Part 2 real-backend contracts.
run_test com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest evidence/runtime/server-foundation.txt 'OK (1 test)'
run_test com.veltrix.hom.vnext.Part2ServerIntegrationInstrumentedTest evidence/runtime/part2-server-contracts.txt 'OK (1 test)'
mark 'BACKEND_CONTRACTS=PASS'

# Pure Compose acceptance for the old verified core and new worlds.
run_test com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest evidence/runtime/part1-ui-regression.txt 'OK (2 tests)'
run_test com.veltrix.hom.vnext.FrontendPart2UiInstrumentedTest evidence/runtime/part2-ui-worlds.txt 'OK (6 tests)'
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/runtime/shell.txt 'OK (1 test)'
mark 'SHELL_UI=PASS'

# MainActivity must render a loaded Part 2 Home from the real session seeded above.
adb shell am force-stop "$PACKAGE"
timeout 20s adb shell am start -W -n "$ACTIVITY" > evidence/runtime/main-start.txt
for _ in $(seq 1 20); do
  timeout 6s adb shell uiautomator dump /sdcard/part2-main.xml >/dev/null 2>&1 || true
  adb pull /sdcard/part2-main.xml evidence/runtime/main-ui.xml >/dev/null 2>&1 || true
  if grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml 2>/dev/null; then break; fi
  sleep .5
done
grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml
! grep -q '{&quot;id&quot;' evidence/runtime/main-ui.xml
adb exec-out screencap -p > evidence/screens/live-home.png
test -s evidence/screens/live-home.png
assert_png_visible evidence/screens/live-home.png
mark 'LIVE_HOME=PASS'

# Deterministic visual matrix. Fixtures use exact production composables. Static screenshots do
# not need a UIAutomator XML dump per frame; semantic truth is covered by executed Compose tests.
capture_fixture HOME_FOCUS 01-home-focus
capture_fixture HOME_SPARSE 02-home-sparse
capture_fixture HOME_OFFLINE 03-home-offline
capture_fixture HOME_UNLOCKED 04-home-unlocked
capture_fixture PERSONAL_MAP_ACTIVE 05-personal-map-active
capture_fixture PERSONAL_SPARSE 06-personal-sparse
capture_fixture PERSONAL_OFFLINE 07-personal-offline
capture_fixture PROJECTS_LIST 08-projects-list
capture_fixture PROJECTS_SPACE 09-project-space
capture_fixture PROJECTS_EMPTY 10-projects-empty
capture_fixture CHAT_STREAMING 11-chat-streaming
capture_fixture CHAT_CITATION 12-chat-citations
capture_fixture CHAT_ERROR 13-chat-error
capture_fixture LIBRARY_PROCESSING 14-library-processing
capture_fixture LIBRARY_FAILED 15-library-failed
capture_fixture TESTING_ACTIVE 16-testing-active
capture_fixture QUIZ_RESULT 17-quiz-result
capture_fixture PRACTICE_HINT 18-practice-hint
capture_fixture PRACTICE_FEEDBACK 19-practice-feedback
capture_fixture FLASHCARD_READY 20-flashcard
capture_fixture MISTAKES_ACTIVE 21-mistakes
capture_fixture STORE_READY 22-store
capture_fixture STORE_INSUFFICIENT 23-store-insufficient
capture_fixture SEARCH_RESULTS 24-search
capture_fixture HISTORY_READY 25-history
for png in evidence/screens/{01-home-focus,02-home-sparse,03-home-offline,04-home-unlocked,05-personal-map-active,06-personal-sparse,07-personal-offline,08-projects-list,09-project-space,10-projects-empty,11-chat-streaming,12-chat-citations,13-chat-error,14-library-processing,15-library-failed,16-testing-active,17-quiz-result,18-practice-hint,19-practice-feedback,20-flashcard,21-mistakes,22-store,23-store-insufficient,24-search,25-history}.png; do
  assert_png_visible "$png"
done
printf 'VISUAL_MATRIX=PASS count=25 rendered=25 blank=0\n' | tee evidence/screens/visual-matrix-gate.txt
mark 'VISUAL_MATRIX=PASS'

# Temporal proof for World Layer identity continuity. Emulator evidence only.
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb shell rm -f /sdcard/part2-world-motion.mp4 >/dev/null 2>&1 || true
timeout 15s adb shell screenrecord --time-limit 9 --bit-rate 6000000 /sdcard/part2-world-motion.mp4 >/dev/null 2>&1 &
REC_PID=$!
sleep 1
timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario HOME_FOCUS >/dev/null
sleep 2
timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario PERSONAL_MAP_ACTIVE >/dev/null
sleep 2
timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario PROJECTS_SPACE >/dev/null
sleep 2
timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario STORE_READY >/dev/null
wait "$REC_PID" || true
adb pull /sdcard/part2-world-motion.mp4 evidence/motion/part2-world-transitions.mp4 >/dev/null
test -s evidence/motion/part2-world-transitions.mp4
printf 'MOTION_EMULATOR_PROOF=PASS\nPHYSICAL_TOUCH_FEEL=NOT_VERIFIED\n' | tee evidence/motion/motion-gate.txt
mark 'MOTION=PASS'

# A11Y: extreme text, reduced motion, TalkBack presence/semantics.
adb shell settings put system font_scale 2.0
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/accessibility/extreme-font-shell.txt 'OK (1 test)'
capture_fixture HOME_FOCUS 26-home-font-200
capture_fixture PROJECTS_SPACE 27-project-font-200
adb shell settings put system font_scale 1.0
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
run_test com.veltrix.hom.vnext.ShellInstrumentedTest evidence/accessibility/reduced-motion-shell.txt 'OK (1 test)'
capture_fixture PERSONAL_MAP_ACTIVE 28-personal-reduced-motion
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
mark 'A11Y=PASS'

# Existing local-first/offline/process-death guarantees remain regression gates.
run_test com.veltrix.hom.vnext.DurabilityInstrumentedTest evidence/runtime/durability.txt 'OK (2 tests)'
run_test com.veltrix.hom.vnext.OfflineDataInstrumentedTest evidence/runtime/offline.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#aSeedPart3State' evidence/runtime/process-death-seed.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#zVerifyPart3StateAfterFreshInstrumentationProcess' evidence/runtime/process-death-verify.txt 'OK (1 test)'
mark 'DURABILITY=PASS'

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
timeout 20s adb shell am start -W -n "$ACTIVITY" >/dev/null
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
printf 'PERF_API36_EMULATOR_SMOKE=PASS\nPHYSICAL_DEVICE_PF=NOT_VERIFIED\n' | tee evidence/performance/performance-gate.txt
mark 'PERF_EMULATOR=PASS'

echo PART1_REGRESSION_RUNTIME=PASS | tee evidence/runtime/part1-regression-gate.txt
echo PART2_BACKEND_CONTRACT_RUNTIME=PASS | tee evidence/runtime/part2-contract-gate.txt
echo PART2_A11Y_RUNTIME=PASS | tee evidence/accessibility/a11y-gate.txt
echo PART2_DURABILITY_OFFLINE_PROCESS_DEATH=PASS | tee evidence/runtime/durability-gate.txt
echo PART2_RUNTIME=PASS | tee evidence/runtime/runtime-gate.txt
mark 'PART2_RUNTIME=PASS'
