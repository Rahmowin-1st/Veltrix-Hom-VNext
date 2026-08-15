#!/usr/bin/env bash
set -euo pipefail

# Run-scoped proof directories must never inherit checked-in or prior-run evidence.
# Preserve root-level provenance/build/server logs created by earlier workflow stages.
rm -rf evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics
mkdir -p evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics package
APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"
EVIDENCE_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.FrontendEvidenceActivity"
PROGRESS="evidence/runtime/final-progress.txt"
mark(){ printf '%s\n' "$1" | tee -a "$PROGRESS"; }

cleanup(){
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put secure touch_exploration_enabled 0 >/dev/null 2>&1 || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
}
trap cleanup EXIT

test -s "$APK"; test -s "$TEST_APK"
adb reverse tcp:8080 tcp:8080
adb install -r "$APK" >/dev/null
adb install -r "$TEST_APK" >/dev/null
I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"
mark 'DEVICE_READY=PASS'

run_test(){
  local cls="$1" out="$2" expect="${3:-OK}"
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  timeout 180s adb shell am instrument -w -e class "$cls" "$I" > "$out"
  cat "$out"
  grep -q "$expect" "$out"
}

# Fresh instrumentation processes are deliberate: API36 headless focus handoff can deadlock
# when multiple createComposeRule tests share one process. This preserves the accepted Part1 proof model.
run_test 'com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest' evidence/runtime/server-foundation.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part2ServerIntegrationInstrumentedTest' evidence/runtime/part2-server-contracts.txt 'OK (1 test)'
mark 'BACKEND_CONTRACTS=PASS'

run_test 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#homeUsesAuthoritativeSnapshotWithoutLocalEconomyAuthority' evidence/runtime/part1-home.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#personalExposesTrustworthySparseAndMapState' evidence/runtime/part1-personal.txt 'OK (1 test)'
cat evidence/runtime/part1-home.txt evidence/runtime/part1-personal.txt > evidence/runtime/part1-ui-regression.txt
mark 'PART1_UI_REGRESSION=PASS'

for spec in \
  'homeAndPersonalShareLivingIdentityAndServerProgression:p2-home-personal' \
  'personalMapUsesAuthoritativeUnitsRatherThanInventedRoute:p2-map' \
  'projectSpacePresentsContextAsOneWorkspace:p2-projects' \
  'chatRendersStreamingAndCitationWithoutExposingHiddenReasoning:p2-chat' \
  'sourceStatesAndStoreOwnershipRemainExplicit:p2-library-store' \
  'flashcardRatingIsUserActionNotLocalScheduleAuthority:p2-flashcards'
do
  method="${spec%%:*}"; name="${spec##*:}"
  run_test "com.veltrix.hom.vnext.FrontendPart2UiInstrumentedTest#$method" "evidence/runtime/${name}.txt" 'OK (1 test)'
done
cat evidence/runtime/p2-*.txt > evidence/runtime/part2-ui-worlds.txt
run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/runtime/shell.txt 'OK (1 test)'
mark 'SHELL_UI=PASS'

assert_png_complete(){
python3 - "$1" <<'PYPNG'
import sys,struct,zlib,math
p=sys.argv[1]; b=open(p,'rb').read()
if b[:8] != b'\x89PNG\r\n\x1a\n': raise SystemExit(41)
pos=8; packed=b''; w=h=bd=ct=None
while pos < len(b):
 n=struct.unpack('>I',b[pos:pos+4])[0]; typ=b[pos+4:pos+8]; dat=b[pos+8:pos+8+n]; pos += 12+n
 if typ==b'IHDR':
  w,h,bd,ct,_,_,inter=struct.unpack('>IIBBBBB',dat)
  if bd!=8 or inter!=0 or ct not in (0,2,4,6): raise SystemExit(42)
 elif typ==b'IDAT': packed+=dat
 elif typ==b'IEND': break
ch={0:1,2:3,4:2,6:4}[ct]; bpp=ch; stride=w*ch; raw=zlib.decompress(packed); off=0; prev=bytearray(stride); rows=[]; allv=[]
step=max(1,w//120)
for y in range(h):
 f=raw[off]; off+=1; cur=bytearray(raw[off:off+stride]); off+=stride
 for x in range(stride):
  a=cur[x-bpp] if x>=bpp else 0; bb=prev[x]; c=prev[x-bpp] if x>=bpp else 0
  if f==1: cur[x]=(cur[x]+a)&255
  elif f==2: cur[x]=(cur[x]+bb)&255
  elif f==3: cur[x]=(cur[x]+((a+bb)//2))&255
  elif f==4:
   q=a+bb-c; pa=abs(q-a); pb=abs(q-bb); pc=abs(q-c); pr=a if pa<=pb and pa<=pc else (bb if pb<=pc else c); cur[x]=(cur[x]+pr)&255
  elif f!=0: raise SystemExit(43)
 vals=[]
 for px in range(0,w,step):
  x=px*ch; vals.append(sum(cur[x:x+3])/3 if ct in (2,6) else cur[x])
 rm=sum(vals)/len(vals); rows.append(rm); allv.extend(vals); prev=cur
mean=sum(allv)/len(allv); std=math.sqrt(sum((v-mean)**2 for v in allv)/len(allv))
def band(a,b):
 s=rows[int(h*a):max(int(h*b),int(h*a)+1)]; return sum(s)/len(s)
bands=[band(.10,.30),band(.40,.60),band(.70,.90)]
print(f'COMPLETE_PNG {p} mean={mean:.1f} std={std:.1f} bands={bands}')
# Product evidence background is intentionally light. A black/half-presented frame fails any band.
if mean < 105 or std < 4 or min(bands) < 82: raise SystemExit(44)
PYPNG
}

scenario_marker(){
 case "$1" in
  HOME_FOCUS) echo 'Retest Newton' ;; HOME_SPARSE) echo 'New learner' ;; HOME_OFFLINE) echo 'Offline' ;; HOME_UNLOCKED) echo 'Retest Newton|Continue' ;;
  PERSONAL_MAP_ACTIVE) echo 'Personal Map|Motion' ;; PERSONAL_SPARSE) echo 'New learner' ;; PERSONAL_OFFLINE) echo 'Offline' ;;
  PROJECTS_LIST) echo 'Motion Studio' ;; PROJECTS_SPACE) echo 'PROJECT SPACE|Project Space' ;; PROJECTS_EMPTY) echo 'Create a project' ;;
  CHAT_STREAMING) echo 'Building a grounded explanation' ;; CHAT_CITATION) echo 'Citations|Newton.s second law' ;; CHAT_ERROR) echo 'temporarily unavailable|Retry' ;;
  LIBRARY_PROCESSING) echo 'Processing' ;; LIBRARY_FAILED) echo 'Failed|Retry processing' ;;
  TESTING_ACTIVE) echo 'Motion checkpoint' ;; QUIZ_RESULT) echo 'Motion checkpoint|Result|50' ;;
  PRACTICE_HINT) echo 'Use F = ma' ;; PRACTICE_FEEDBACK) echo 'Correct' ;; FLASHCARD_READY) echo 'What is acceleration' ;; MISTAKES_ACTIVE) echo 'Mistakes|Mechanics' ;;
  STORE_READY) echo 'Store|Coin balance|Focused learning identity' ;; STORE_INSUFFICIENT) echo 'Store|Insufficient|20' ;;
  SEARCH_RESULTS) echo 'Search|Motion Studio' ;; HISTORY_READY) echo 'History|Practice Completed|Assessment Completed' ;;
  *) echo '.+' ;;
 esac
}

capture_fixture(){
 local scenario="$1" name="$2" marker xml png
 marker="$(scenario_marker "$scenario")"; xml="evidence/screens/${name}.xml"; png="evidence/screens/${name}.png"
 adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
 adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
 adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
 timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario "$scenario" > "evidence/screens/${name}-start.txt"
 for attempt in 1 2 3 4 5 6 7 8 9 10; do
  sleep .45
  timeout 6s adb shell uiautomator dump /sdcard/part2-proof.xml >/dev/null 2>&1 || true
  adb pull /sdcard/part2-proof.xml "$xml" >/dev/null 2>&1 || true
  if grep -Eqi "$marker" "$xml" 2>/dev/null; then
   adb exec-out screencap -p > "$png"
   if test -s "$png" && assert_png_complete "$png"; then
    printf 'scenario=%s marker=%s attempt=%s semantic=PASS visual=PASS\n' "$scenario" "$marker" "$attempt" >> "evidence/screens/${name}-start.txt"
    return 0
   fi
  fi
 done
 printf 'scenario=%s marker=%s semantic_or_visual=FAIL\n' "$scenario" "$marker" >> "evidence/screens/${name}-start.txt"
 cat "$xml" 2>/dev/null || true
 return 1
}

# Real MainActivity shell must expose live backend-seeded Home and a complete rendered frame.
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
assert_png_complete evidence/screens/live-home.png
mark 'LIVE_HOME=PASS'

while read -r scenario name; do capture_fixture "$scenario" "$name" </dev/null; done <<'MATRIX'
HOME_FOCUS 01-home-focus
HOME_SPARSE 02-home-sparse
HOME_OFFLINE 03-home-offline
HOME_UNLOCKED 04-home-unlocked
PERSONAL_MAP_ACTIVE 05-personal-map-active
PERSONAL_SPARSE 06-personal-sparse
PERSONAL_OFFLINE 07-personal-offline
PROJECTS_LIST 08-projects-list
PROJECTS_SPACE 09-project-space
PROJECTS_EMPTY 10-projects-empty
CHAT_STREAMING 11-chat-streaming
CHAT_CITATION 12-chat-citations
CHAT_ERROR 13-chat-error
LIBRARY_PROCESSING 14-library-processing
LIBRARY_FAILED 15-library-failed
TESTING_ACTIVE 16-testing-active
QUIZ_RESULT 17-quiz-result
PRACTICE_HINT 18-practice-hint
PRACTICE_FEEDBACK 19-practice-feedback
FLASHCARD_READY 20-flashcard
MISTAKES_ACTIVE 21-mistakes
STORE_READY 22-store
STORE_INSUFFICIENT 23-store-insufficient
SEARCH_RESULTS 24-search
HISTORY_READY 25-history
MATRIX
MATRIX_NAMES=(
  01-home-focus 02-home-sparse 03-home-offline 04-home-unlocked
  05-personal-map-active 06-personal-sparse 07-personal-offline
  08-projects-list 09-project-space 10-projects-empty
  11-chat-streaming 12-chat-citations 13-chat-error
  14-library-processing 15-library-failed 16-testing-active 17-quiz-result
  18-practice-hint 19-practice-feedback 20-flashcard 21-mistakes
  22-store 23-store-insufficient 24-search 25-history
)
MATRIX_FILES=()
: > evidence/screens/screenshot-sha256.txt
for name in "${MATRIX_NAMES[@]}"; do
  png="evidence/screens/${name}.png"
  start="evidence/screens/${name}-start.txt"
  test -s "$png"
  grep -q 'semantic=PASS visual=PASS' "$start"
  assert_png_complete "$png"
  MATRIX_FILES+=("$png")
done
test "${#MATRIX_FILES[@]}" -eq 25
sha256sum "${MATRIX_FILES[@]}" > evidence/screens/screenshot-sha256.txt
test "$(wc -l < evidence/screens/screenshot-sha256.txt)" -eq 25
printf 'VISUAL_MATRIX=PASS count=%s semantic=%s complete_render=%s fresh_manifest=PASS\n' "${#MATRIX_FILES[@]}" "${#MATRIX_FILES[@]}" "${#MATRIX_FILES[@]}" | tee evidence/screens/visual-matrix-gate.txt
mark 'VISUAL_MATRIX=PASS'

motion_state(){
 local scenario="$1" marker="$2" name="$3"
 local xml="evidence/motion/${name}.xml"
 # Keep a single EvidenceActivity instance alive throughout the temporal proof. `am start -W`
 # can wait for a fresh Activity resume and hit the 20s guard on the software-rendered API36
 # runner. SINGLE_TOP instead delivers the new scenario through onNewIntent; the semantic
 # polling below is the actual readiness gate, so no launch-completion guess is accepted.
 rm -f "$xml"
 adb shell rm -f /sdcard/part2-motion.xml >/dev/null 2>&1 || true
 timeout 8s adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario "$scenario" >/dev/null
 for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep .35
  timeout 6s adb shell uiautomator dump /sdcard/part2-motion.xml >/dev/null 2>&1 || true
  adb pull /sdcard/part2-motion.xml "$xml" >/dev/null 2>&1 || true
  if grep -Eqi "$marker" "$xml" 2>/dev/null; then
   echo "scenario=$scenario attempt=$attempt state=PASS" | tee -a evidence/motion/motion-sequence-gate.txt
   return 0
  fi
 done
 echo "scenario=$scenario state=FAIL" | tee -a evidence/motion/motion-sequence-gate.txt
 return 1
}
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
: > evidence/motion/motion-sequence-gate.txt
motion_state HOME_FOCUS 'Retest Newton' home
adb shell rm -f /sdcard/part2-world-motion.mp4 >/dev/null 2>&1 || true
timeout 26s adb shell screenrecord --time-limit 20 --bit-rate 6000000 /sdcard/part2-world-motion.mp4 >/dev/null 2>&1 & REC=$!
sleep .8
motion_state PERSONAL_MAP_ACTIVE 'Personal Map|Motion' personal
motion_state PROJECTS_SPACE 'PROJECT SPACE|Project Space' projects
motion_state STORE_READY 'Store|Coin balance|Focused learning identity' store
motion_state HOME_FOCUS 'Retest Newton' home-return
wait "$REC" || true
adb pull /sdcard/part2-world-motion.mp4 evidence/motion/part2-world-transitions.mp4 >/dev/null
test -s evidence/motion/part2-world-transitions.mp4
python3 - <<'PYMOTION'
from pathlib import Path
lines=[line.strip() for line in Path('evidence/motion/motion-sequence-gate.txt').read_text().splitlines() if 'state=PASS' in line]
scenarios=[line.split('scenario=',1)[1].split(' ',1)[0] for line in lines]
expected=['HOME_FOCUS','PERSONAL_MAP_ACTIVE','PROJECTS_SPACE','STORE_READY','HOME_FOCUS']
print(f'MOTION_SEQUENCE observed={scenarios}')
if scenarios != expected:
    raise SystemExit(45)
PYMOTION
for xml_name in home personal projects store home-return; do test -s "evidence/motion/${xml_name}.xml"; done
printf 'MOTION_RUNTIME=PASS states=HOME,PERSONAL,PROJECTS,STORE,HOME named_semantic_dumps=PASS\nPHYSICAL_TOUCH_FEEL=NOT_VERIFIED\n' | tee evidence/motion/motion-gate.txt
mark 'MOTION=PASS'

adb shell settings put system font_scale 2.0
run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/accessibility/extreme-font-shell.txt 'OK (1 test)'
capture_fixture HOME_FOCUS 26-home-font-200
capture_fixture PROJECTS_SPACE 27-project-font-200
adb shell settings put system font_scale 1.0
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/accessibility/reduced-motion-shell.txt 'OK (1 test)'
capture_fixture PERSONAL_MAP_ACTIVE 28-personal-reduced-motion
adb shell settings put global animator_duration_scale 1; adb shell settings put global transition_animation_scale 1; adb shell settings put global window_animation_scale 1

if adb shell pm path com.google.android.marvin.talkback >/dev/null 2>&1; then
 TB='com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
 adb shell settings put secure enabled_accessibility_services "$TB"; adb shell settings put secure accessibility_enabled 1; adb shell settings put secure touch_exploration_enabled 1; sleep 1
 adb shell dumpsys accessibility > evidence/accessibility/talkback-dumpsys.txt; grep -qi talkback evidence/accessibility/talkback-dumpsys.txt
 run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/accessibility/talkback-shell.txt 'OK (1 test)'
 echo 'TALKBACK_RUNTIME=PASS' | tee evidence/accessibility/talkback-gate.txt
else
 echo 'TALKBACK_RUNTIME=UNAVAILABLE_IN_GOOGLE_APIS_IMAGE;SEMANTICS_TREE_EXECUTED=PASS' | tee evidence/accessibility/talkback-gate.txt
fi
mark 'A11Y_ADAPTATION=PASS'
cleanup

run_test 'com.veltrix.hom.vnext.DurabilityInstrumentedTest' evidence/runtime/durability.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest' evidence/runtime/offline.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#aSeedPart3State' evidence/runtime/process-death-seed.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#zVerifyPart3StateAfterFreshInstrumentationProcess' evidence/runtime/process-death-verify.txt 'OK (1 test)'
mark 'DURABILITY=PASS'

{
 echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
 echo "hardware=$(adb shell getprop ro.hardware | tr -d '\r')"
 echo "egl=$(adb shell getprop ro.hardware.egl | tr -d '\r')"
 echo "refresh=$(adb shell dumpsys display | grep -E 'mRefreshRate|fps=' | head -8 || true)"
 adb shell dumpsys SurfaceFlinger 2>/dev/null | grep -Ei 'GLES|OpenGL|Vulkan|renderer|vendor' | head -50 || true
} | tee evidence/performance/renderer-environment.txt
adb shell am force-stop "$PACKAGE"; adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
timeout 20s adb shell am start -W -n "$ACTIVITY" >/dev/null; sleep 2
adb shell input tap 405 2240 >/dev/null 2>&1 || true; sleep .5
adb shell input tap 135 2240 >/dev/null 2>&1 || true; sleep .5
adb shell input swipe 2 1000 800 1000 650 >/dev/null 2>&1 || true; sleep .5
adb shell input swipe 800 1000 2 1000 650 >/dev/null 2>&1 || true
adb shell dumpsys gfxinfo "$PACKAGE" framestats > evidence/performance/gfxinfo-smoke.txt || true
adb shell dumpsys meminfo "$PACKAGE" > evidence/performance/meminfo.txt || true
adb shell logcat -d -t 1500 > evidence/runtime/logcat.txt || true
! grep -q 'ANR in com.veltrix.hom.vnext' evidence/runtime/logcat.txt
! grep -q 'FATAL EXCEPTION: main' evidence/runtime/logcat.txt
printf 'PERF_API36_RUNTIME_SMOKE=PASS\nPHYSICAL_DEVICE_PF=NOT_VERIFIED\n' | tee evidence/performance/performance-gate.txt
mark 'PERF_RUNTIME=PASS'

sha256sum "$APK" | tee evidence/apk-sha256.txt
sha256sum "$TEST_APK" | tee evidence/androidtest-apk-sha256.txt
git archive --format=zip --output=evidence/Veltrix-Hom-Frontend-Part2-EXACT-SOURCE.zip "$GITHUB_SHA"
sha256sum evidence/Veltrix-Hom-Frontend-Part2-EXACT-SOURCE.zip | tee evidence/source-sha256.txt

cat > "evidence/VELTRIX_FRONTEND_PART2_MANAGER_HANDOFF_2026-08-15.md" <<EOF
# VELTRIX FRONTEND PART 2 — MANAGER HANDOFF

- Repository: ${GITHUB_REPOSITORY}
- Branch: ${GITHUB_REF_NAME}
- Exact tested SHA: ${GITHUB_SHA}
- Accepted Backend SHA: 4ed13cfcf7d4bb1fe6215b231426e0b4f208343a
- Previous Part1 SHA: ae8499fec896bd8715f5f114fc0eb95f12e8ebe1
- CI run: ${GITHUB_RUN_ID}

## Closure ledger
- Part1 inherited UI regression: PASS in fresh instrumentation processes.
- Part2 backend contracts: PASS against accepted server runtime.
- Part2 UI worlds: PASS with isolated Compose instrumentation.
- Main shell/live Home: PASS.
- Visual matrix: 25/25 scenario-semantic + complete-render proof.
- Motion world continuity: PASS on API36 runtime; physical touch-feel remains externally unverified.
- Extreme font + reduced motion: PASS.
- TalkBack: see evidence/accessibility/talkback-gate.txt; no claim beyond executed environment.
- Offline/durability/process death: PASS.
- Performance: API36 runtime smoke + renderer fingerprint captured; physical-device PF not fabricated.
- Backend-owned server/database/core main source protected by workflow provenance gate.

## Exact artifacts/hashes
See evidence/apk-sha256.txt, evidence/androidtest-apk-sha256.txt, evidence/source-sha256.txt and package/SHA256SUMS.txt.

## Manager independent review
Review visual screenshots, motion MP4, renderer environment, accessibility gate, exact source provenance, and the physical-device limitation before Manager acceptance.
EOF

# Revalidate the visual evidence at the end of the run so packaging cannot inherit stale/missing proof.
test "$(wc -l < evidence/screens/screenshot-sha256.txt)" -eq 25
sha256sum -c evidence/screens/screenshot-sha256.txt
for name in 26-home-font-200 27-project-font-200 28-personal-reduced-motion; do test -s "evidence/screens/${name}.png"; done
test "$(find evidence/screens -maxdepth 1 -type f -name '*.png' | wc -l)" -eq 29
printf 'PART1_ACCEPTANCE_CANDIDATE=PASS\nPART2_ACCEPTANCE_CANDIDATE=PASS\nPHYSICAL_DEVICE_EXTERNAL_PROOF=NOT_VERIFIED\nSCREENSHOT_ARTIFACT_SET=PASS fresh_matrix=25 total_png=29\n' | tee evidence/runtime/final-gate.txt
mark 'PART2_FINAL_PROOF=PASS'
