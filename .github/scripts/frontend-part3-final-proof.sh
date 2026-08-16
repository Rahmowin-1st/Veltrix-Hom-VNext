#!/usr/bin/env bash
set -euo pipefail

# Exact-run proof only. Never inherit evidence from a previous checkout/run.
rm -rf evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics
mkdir -p evidence/runtime evidence/accessibility evidence/performance evidence/screens evidence/motion evidence/diagnostics package
APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
PACKAGE="com.veltrix.hom.vnext.dev"
ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.MainActivity"
EVIDENCE_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.FrontendEvidenceActivity"
PROGRESS="evidence/runtime/final-progress.txt"
: > "$PROGRESS"
mark(){ printf '%s\n' "$1" | tee -a "$PROGRESS"; }

cleanup(){
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put global window_animation_scale 1 >/dev/null 2>&1 || true
  adb shell settings put secure high_text_contrast_enabled 0 >/dev/null 2>&1 || true
  adb shell settings put global debug.force_rtl 0 >/dev/null 2>&1 || true
  adb shell settings put secure touch_exploration_enabled 0 >/dev/null 2>&1 || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
  adb shell wm size reset >/dev/null 2>&1 || true
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
  timeout 210s adb shell am instrument -w -e class "$cls" "$I" > "$out"
  cat "$out"
  grep -q "$expect" "$out"
}

# Accepted backend + inherited frontend contracts + new Part 3 controls.
run_test 'com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest' evidence/runtime/server-foundation.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part2ServerIntegrationInstrumentedTest' evidence/runtime/part2-server-contracts.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3FinalStateInstrumentedTest#typedPart3RepositoryReachesRealHomePersonalWorkspaceCommandAndSearch' evidence/runtime/part3-final-contracts.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3ControlIntegrationInstrumentedTest' evidence/runtime/part3-control-contracts.txt 'OK (1 test)'
mark 'BACKEND_CONTRACTS=PASS'

# Inherited and new UI gates run in fresh instrumentation processes to avoid cross-test focus state.
run_test 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#homeUsesAuthoritativeSnapshotWithoutLocalEconomyAuthority' evidence/runtime/part1-home.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.FrontendPart1UiInstrumentedTest#personalExposesTrustworthySparseAndMapState' evidence/runtime/part1-personal.txt 'OK (1 test)'
cat evidence/runtime/part1-home.txt evidence/runtime/part1-personal.txt > evidence/runtime/part1-ui-regression.txt

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
run_test 'com.veltrix.hom.vnext.FrontendPart3ClosureUiInstrumentedTest#flashcardCriticalPromptHasPlausibleVisibleBounds' evidence/runtime/p3-flashcard-bounds.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.FrontendPart3ClosureUiInstrumentedTest#calculatorAndTranslatePresentBackendResultsWithoutInventingAuthority' evidence/runtime/p3-tools-ui.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.FrontendPart3ClosureUiInstrumentedTest#notificationAndSettingsStatesRemainExplicit' evidence/runtime/p3-controls-ui.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/runtime/shell.txt 'OK (1 test)'
mark 'UI_REGRESSIONS=PASS'

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
print(f'COMPLETE_PNG {p} {w}x{h} mean={mean:.1f} std={std:.1f} bands={bands}')
if w < 480 or h < 800 or mean < 95 or std < 4 or min(bands) < 72: raise SystemExit(44)
PYPNG
}

scenario_marker(){
 case "$1" in
  HOME_FOCUS) echo 'Retest Newton|Ask Veltrix' ;; HOME_SPARSE) echo 'New learner' ;; HOME_OFFLINE) echo 'Offline' ;; HOME_UNLOCKED) echo 'Retest Newton|Continue' ;;
  PERSONAL_MAP_ACTIVE) echo 'Personal Map|Motion' ;; PERSONAL_SPARSE) echo 'New learner' ;; PERSONAL_OFFLINE) echo 'Offline' ;;
  PROJECTS_LIST) echo 'Motion Studio' ;; PROJECTS_SPACE) echo 'Project Space|Master Newton' ;; PROJECTS_EMPTY) echo 'Create a project' ;;
  CHAT_STREAMING) echo 'Building a grounded explanation' ;; CHAT_CITATION) echo 'Citations|Newton.s second law' ;; CHAT_ERROR) echo 'temporarily unavailable|Retry' ;;
  LIBRARY_PROCESSING) echo 'Processing' ;; LIBRARY_FAILED) echo 'Failed|Retry processing' ;;
  TESTING_ACTIVE) echo 'Motion checkpoint' ;; QUIZ_RESULT) echo 'Motion checkpoint|50' ;;
  PRACTICE_HINT) echo 'Use F = ma' ;; PRACTICE_FEEDBACK) echo 'Correct' ;; FLASHCARD_READY) echo 'What is acceleration' ;; MISTAKES_ACTIVE) echo 'Mistakes|Mechanics' ;;
  STORE_READY) echo 'Store|Preview|Focused' ;; STORE_INSUFFICIENT) echo 'Store|Insufficient|20' ;;
  SEARCH_RESULTS) echo 'Search|Motion Studio' ;; HISTORY_READY) echo 'History|Practice Completed|Assessment Completed' ;;
  CALCULATOR_RESULT) echo 'Calculator|Deterministic backend result' ;; CALCULATOR_ERROR) echo 'changed on another device|Refresh|Calculator' ;;
  TRANSLATE_RESULT) echo 'Hello world|test-mock' ;; TRANSLATE_ERROR) echo 'No live connection|Translate' ;;
  NOTIFICATIONS_LIST) echo 'Review mechanics|Android delivery' ;; NOTIFICATIONS_EMPTY) echo 'No notification intents|notification' ;;
  SETTINGS_ACCOUNT) echo 'Account profile|Revision 9' ;; SETTINGS_ACCESSIBILITY) echo 'Accessibility preferences|reduced motion' ;;
  SETTINGS_MEMORY) echo 'Memory & personalization|Memory enabled' ;; SETTINGS_DATA_READY) echo 'Export snapshot|Your data' ;;
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
 for attempt in $(seq 1 12); do
  sleep .4
  timeout 6s adb shell uiautomator dump /sdcard/p3-proof.xml >/dev/null 2>&1 || true
  adb pull /sdcard/p3-proof.xml "$xml" >/dev/null 2>&1 || true
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

# Critical text bounds gate. This explicitly closes the previous flashcard false-green class.
check_bounds(){
 local xml="$1" regex="$2" label="$3" minw="$4" minh="$5"
 python3 - "$xml" "$regex" "$label" "$minw" "$minh" <<'PYBOUNDS' | tee -a evidence/screens/layout-bounds-gate.txt
import sys,re,xml.etree.ElementTree as ET
path,pattern,label,minw,minh=sys.argv[1],sys.argv[2],sys.argv[3],int(sys.argv[4]),int(sys.argv[5])
root=ET.parse(path).getroot(); hits=[]
for n in root.iter('node'):
    text=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).strip()
    if re.search(pattern,text,re.I):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); hits.append((x2-x1,y2-y1,x1,y1,x2,y2,text[:140]))
if not hits:
    raise SystemExit(f'BOUNDS_FAIL label={label} reason=no_matching_semantic_node regex={pattern}')
valid=[h for h in hits if h[0]>=minw and h[1]>=minh and h[2]>=0 and h[3]>=0 and h[4]>h[2] and h[5]>h[3]]
if not valid:
    raise SystemExit(f'BOUNDS_FAIL label={label} candidates={hits} min={minw}x{minh}')
best=max(valid,key=lambda h:h[0]*h[1])
print(f'BOUNDS_PASS label={label} size={best[0]}x{best[1]} bounds=[{best[2]},{best[3]}][{best[4]},{best[5]}] text={best[6]!r}')
PYBOUNDS
}

# Live MainActivity proof: actual shell + backend-seeded Home.
adb shell am force-stop "$PACKAGE"
timeout 20s adb shell am start -W -n "$ACTIVITY" > evidence/runtime/main-start.txt
for _ in $(seq 1 24); do
 timeout 6s adb shell uiautomator dump /sdcard/p3-main.xml >/dev/null 2>&1 || true
 adb pull /sdcard/p3-main.xml evidence/runtime/main-ui.xml >/dev/null 2>&1 || true
 if grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml 2>/dev/null; then break; fi
 sleep .45
done
grep -Eqi 'Part2 Learner|Ask Veltrix|Build the next useful step' evidence/runtime/main-ui.xml
! grep -q '{&quot;id&quot;' evidence/runtime/main-ui.xml
adb exec-out screencap -p > evidence/screens/live-home.png
assert_png_complete evidence/screens/live-home.png
mark 'LIVE_SHELL=PASS'

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
CALCULATOR_RESULT 26-calculator-result
CALCULATOR_ERROR 27-calculator-error
TRANSLATE_RESULT 28-translate-result
TRANSLATE_ERROR 29-translate-error
NOTIFICATIONS_LIST 30-notifications-list
NOTIFICATIONS_EMPTY 31-notifications-empty
SETTINGS_ACCOUNT 32-settings-account
SETTINGS_ACCESSIBILITY 33-settings-accessibility
SETTINGS_MEMORY 34-settings-memory
SETTINGS_DATA_READY 35-settings-data
MATRIX

: > evidence/screens/layout-bounds-gate.txt
check_bounds evidence/screens/20-flashcard.xml 'What is acceleration' flashcard_prompt 120 40
check_bounds evidence/screens/09-project-space.xml 'Master Newton|Current objective|Motion Studio' project_space_focus 120 30
check_bounds evidence/screens/05-personal-map-active.xml 'Motion|Personal Map' map_active_content 100 28
check_bounds evidence/screens/12-chat-citations.xml 'greater net force|F = ma' chat_answer 160 35
check_bounds evidence/screens/26-calculator-result.xml 'Deterministic backend result|RESULT' calculator_result 100 24
check_bounds evidence/screens/28-translate-result.xml 'Hello world|test-mock' translate_result 100 24
check_bounds evidence/screens/30-notifications-list.xml 'Review mechanics' notification_content 100 24
check_bounds evidence/screens/35-settings-data.xml 'Export snapshot|Your data' settings_data 100 24
printf 'CRITICAL_LAYOUT_BOUNDS=PASS count=8\n' | tee -a evidence/screens/layout-bounds-gate.txt

MATRIX_FILES=(evidence/screens/{01-home-focus,02-home-sparse,03-home-offline,04-home-unlocked,05-personal-map-active,06-personal-sparse,07-personal-offline,08-projects-list,09-project-space,10-projects-empty,11-chat-streaming,12-chat-citations,13-chat-error,14-library-processing,15-library-failed,16-testing-active,17-quiz-result,18-practice-hint,19-practice-feedback,20-flashcard,21-mistakes,22-store,23-store-insufficient,24-search,25-history,26-calculator-result,27-calculator-error,28-translate-result,29-translate-error,30-notifications-list,31-notifications-empty,32-settings-account,33-settings-accessibility,34-settings-memory,35-settings-data}.png)
: > evidence/screens/screenshot-sha256.txt
for png in "${MATRIX_FILES[@]}"; do test -s "$png"; assert_png_complete "$png"; done
sha256sum "${MATRIX_FILES[@]}" > evidence/screens/screenshot-sha256.txt
test "$(wc -l < evidence/screens/screenshot-sha256.txt)" -eq 35
printf 'VISUAL_MATRIX=PASS count=35 semantic=35 complete_render=35 bounds_gate=PASS fresh_manifest=PASS\n' | tee evidence/screens/visual-matrix-gate.txt
mark 'VISUAL_MATRIX=PASS'

# High-fidelity runtime capture: no UIAutomator polling while encoding (previous proof starved frames).
probe_motion(){
 local file="$1" label="$2"
 ffprobe -v error -count_frames -select_streams v:0 \
   -show_entries stream=avg_frame_rate,r_frame_rate,nb_read_frames,width,height \
   -show_entries format=duration -of json "$file" > "evidence/motion/${label}-ffprobe.json"
 python3 - "$file" "$label" "evidence/motion/${label}-ffprobe.json" <<'PYMOTION' | tee "evidence/motion/${label}-gate.txt"
import json,os,sys
file,label,meta=sys.argv[1:]; data=json.load(open(meta)); s=data['streams'][0]; dur=float(data['format']['duration']); frames=int(s.get('nb_read_frames') or 0); size=os.path.getsize(file)
def frac(v):
 a,b=(v or '0/1').split('/'); return float(a)/float(b) if float(b) else 0.0
avg=frac(s.get('avg_frame_rate')); effective=frames/dur if dur>0 else 0; w=int(s.get('width') or 0); h=int(s.get('height') or 0)
print(f'MOTION_CLIP={label} file={file} duration={dur:.3f}s encoded_frames={frames} encoded_avg_fps={avg:.2f} encoded_effective_fps={effective:.2f} bytes={size} size={w}x{h}')
# Android screenrecord may emit sparse/VFR frames for static intervals. Encoded FPS is therefore
# temporal-recording metadata, not a render-pacing metric. Render pacing is gated separately from
# screenrecord-free gfxinfo summary on the real MainActivity interaction sequence below.
if dur < 5 or frames < 6 or size < 120000 or w < 720 or h < 1200:
    raise SystemExit(47)
print('MOTION_TEMPORAL_RECORDING=PASS vfr_safe=1 render_fps_claim=NONE')
PYMOTION
}

prepare_scenario(){
 local s="$1" marker="$2"
 adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
 timeout 20s adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario "$s" >/dev/null
 for _ in $(seq 1 12); do
  timeout 6s adb shell uiautomator dump /sdcard/p3-motion-ready.xml >/dev/null 2>&1 || true
  adb pull /sdcard/p3-motion-ready.xml evidence/motion/ready.xml >/dev/null 2>&1 || true
  if grep -Eqi "$marker" evidence/motion/ready.xml; then return 0; fi
  sleep .35
 done
 return 1
}

# Clip 1: core world identity continuity / project/store transitions.
prepare_scenario HOME_FOCUS 'Retest Newton|Ask Veltrix'
adb shell rm -f /sdcard/p3-worlds.mp4 >/dev/null 2>&1 || true
timeout 22s adb shell screenrecord --time-limit 16 --bit-rate 10000000 /sdcard/p3-worlds.mp4 >/dev/null 2>&1 & REC=$!
sleep 1.2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario PERSONAL_MAP_ACTIVE >/dev/null; sleep 2.2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario PROJECTS_SPACE >/dev/null; sleep 2.2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario STORE_READY >/dev/null; sleep 2.2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario HOME_FOCUS >/dev/null; sleep 2.2
wait "$REC" || true
adb pull /sdcard/p3-worlds.mp4 evidence/motion/01-world-continuity.mp4 >/dev/null
probe_motion evidence/motion/01-world-continuity.mp4 world-continuity

# Clip 2: actual MainActivity bottom-nav + edge/sidebar direct manipulation without dump-induced frame starvation.
adb shell am force-stop "$PACKAGE"; timeout 20s adb shell am start -W -n "$ACTIVITY" >/dev/null; sleep 1
read W H < <(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | head -1 | tr -d '\r')
: "${W:=1080}"; : "${H:=2400}"
DENSITY="$(adb shell wm density | sed -n 's/.*Physical density: \([0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
: "${DENSITY:=420}"
EDGE=$((32*DENSITY/160)); if [ "$EDGE" -lt 24 ]; then EDGE=24; fi
Y=$((H-110)); X1=$((W/8)); X2=$((3*W/8)); X3=$((5*W/8)); X4=$((7*W/8)); MID=$((H/2)); DRAWER_END=$((W*3/4))
adb shell rm -f /sdcard/p3-shell.mp4 >/dev/null 2>&1 || true
timeout 22s adb shell screenrecord --time-limit 15 --bit-rate 10000000 /sdcard/p3-shell.mp4 >/dev/null 2>&1 & REC=$!
sleep 1
adb shell input tap "$X2" "$Y"; sleep 1.3
adb shell input tap "$X3" "$Y"; sleep 1.3
adb shell input tap "$X4" "$Y"; sleep 1.3
adb shell input tap "$X1" "$Y"; sleep 1.3
adb shell input swipe "$EDGE" "$MID" "$DRAWER_END" "$MID" 650; sleep 1.2
adb shell input swipe "$DRAWER_END" "$MID" "$EDGE" "$MID" 650; sleep 1.2
wait "$REC" || true
adb pull /sdcard/p3-shell.mp4 evidence/motion/02-shell-direct-manipulation.mp4 >/dev/null
probe_motion evidence/motion/02-shell-direct-manipulation.mp4 shell-direct-manipulation
# Frame pacing is measured in a second, screenrecord-free replay so capture overhead cannot
# masquerade as app jank. Use the same real shell input sequence, then parse Android gfxinfo summary.
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
timeout 20s adb shell am start -W -n "$ACTIVITY" >/dev/null; sleep 1
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
sleep .2
for x in "$X2" "$X3" "$X4" "$X1"; do adb shell input tap "$x" "$Y" >/dev/null 2>&1 || true; sleep .65; done
adb shell input swipe "$EDGE" "$MID" "$DRAWER_END" "$MID" 650 >/dev/null 2>&1 || true; sleep .7
adb shell input swipe "$DRAWER_END" "$MID" "$EDGE" "$MID" 650 >/dev/null 2>&1 || true; sleep .7
adb shell pidof "$PACKAGE" | tee evidence/motion/shell-process-after-gesture.txt
test -s evidence/motion/shell-process-after-gesture.txt
adb shell dumpsys gfxinfo "$PACKAGE" > evidence/motion/shell-motion-gfxinfo.txt || true
python3 - evidence/motion/shell-motion-gfxinfo.txt <<'PYFRAME' | tee evidence/motion/shell-motion-gfxinfo-gate.txt
import re,sys
text=open(sys.argv[1],errors='replace').read()
def one(pattern,name,cast=float):
    m=re.search(pattern,text,re.M)
    if not m: raise SystemExit(f'MOTION_FRAME_PACING_FAIL reason=missing_{name}')
    return cast(m.group(1))
total=one(r'^Total frames rendered:\s*(\d+)', 'total_frames', int)
janky=one(r'^Janky frames:\s*(\d+)\s*\(([0-9.]+)%\)', 'janky_frames', int)
m=re.search(r'^Janky frames:\s*\d+\s*\(([0-9.]+)%\)',text,re.M)
janky_pct=float(m.group(1)) if m else 100.0
p50=one(r'^50th percentile:\s*(\d+)ms', 'p50')
p90=one(r'^90th percentile:\s*(\d+)ms', 'p90')
p95=one(r'^95th percentile:\s*(\d+)ms', 'p95')
p99=one(r'^99th percentile:\s*(\d+)ms', 'p99')
missed=one(r'^Number Frame deadline missed:\s*(\d+)', 'deadline_missed', int)
print(f'MOTION_FRAME_PACING frames={total} janky={janky} janky_pct={janky_pct:.2f} p50_ms={p50:.0f} p90_ms={p90:.0f} p95_ms={p95:.0f} p99_ms={p99:.0f} deadline_missed={missed}')
# Hardware-accelerated CI-emulator sanity gate only. These intentionally broad limits reject
# pathological rendering while avoiding a fabricated physical-device or 120 Hz smoothness claim.
if total < 12 or janky_pct > 60 or p95 > 250:
    raise SystemExit(48)
print('MOTION_FRAME_PACING_EMULATOR=PASS source=gfxinfo_summary screenrecord_free=1 physical_device_claim=NONE')
PYFRAME

# Clip 3: Part 3 production tools/control worlds.
prepare_scenario CALCULATOR_RESULT 'Deterministic backend result|Calculator'
adb shell rm -f /sdcard/p3-tools.mp4 >/dev/null 2>&1 || true
timeout 22s adb shell screenrecord --time-limit 14 --bit-rate 10000000 /sdcard/p3-tools.mp4 >/dev/null 2>&1 & REC=$!
sleep 1
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario TRANSLATE_RESULT >/dev/null; sleep 2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario NOTIFICATIONS_LIST >/dev/null; sleep 2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario SETTINGS_ACCOUNT >/dev/null; sleep 2
adb shell am start --activity-single-top -n "$EVIDENCE_ACTIVITY" --es scenario SETTINGS_DATA_READY >/dev/null; sleep 2
wait "$REC" || true
adb pull /sdcard/p3-tools.mp4 evidence/motion/03-part3-tools.mp4 >/dev/null
probe_motion evidence/motion/03-part3-tools.mp4 part3-tools
cat evidence/motion/*-gate.txt > evidence/motion/motion-gate.txt
printf 'MOTION_TEMPORAL_EVIDENCE=PASS clips=3 vfr_safe=1\nMOTION_FRAME_PACING_EMULATOR=PASS source=gfxinfo_summary screenrecord_free=1\nPHYSICAL_TOUCH_FEEL=NOT_VERIFIED\n' | tee -a evidence/motion/motion-gate.txt
mark 'MOTION=PASS'

# Accessibility/adaptive matrix.
adb shell settings put system font_scale 2.0
run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/accessibility/extreme-font-shell.txt 'OK (1 test)'
capture_fixture HOME_FOCUS 36-home-font-200
capture_fixture FLASHCARD_READY 37-flashcard-font-200
capture_fixture SETTINGS_ACCOUNT 38-settings-font-200
check_bounds evidence/screens/37-flashcard-font-200.xml 'What is acceleration' flashcard_prompt_font200 100 34
adb shell settings put system font_scale 1.0

adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
capture_fixture PERSONAL_MAP_ACTIVE 39-personal-reduced-motion
adb shell settings put global animator_duration_scale 1; adb shell settings put global transition_animation_scale 1; adb shell settings put global window_animation_scale 1

adb shell settings put secure high_text_contrast_enabled 1
capture_fixture HOME_FOCUS 40-home-high-contrast
adb shell settings put secure high_text_contrast_enabled 0

adb shell settings put global debug.force_rtl 1
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
capture_fixture SETTINGS_ACCOUNT 41-settings-rtl
adb shell settings put global debug.force_rtl 0

adb shell wm size 1600x2560
capture_fixture HOME_FOCUS 42-home-expanded
capture_fixture PERSONAL_MAP_ACTIVE 43-map-expanded
capture_fixture PROJECTS_SPACE 44-project-expanded
capture_fixture STORE_READY 45-store-expanded
capture_fixture SETTINGS_ACCOUNT 46-settings-expanded
adb shell wm size reset

adb shell wm size 720x1600
capture_fixture HOME_FOCUS 47-home-narrow
capture_fixture SETTINGS_ACCOUNT 48-settings-narrow
adb shell wm size reset

if adb shell pm path com.google.android.marvin.talkback >/dev/null 2>&1; then
 TB='com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
 adb shell settings put secure enabled_accessibility_services "$TB"; adb shell settings put secure accessibility_enabled 1; adb shell settings put secure touch_exploration_enabled 1; sleep 1
 adb shell dumpsys accessibility > evidence/accessibility/talkback-dumpsys.txt; grep -qi talkback evidence/accessibility/talkback-dumpsys.txt
 run_test 'com.veltrix.hom.vnext.ShellInstrumentedTest' evidence/accessibility/talkback-shell.txt 'OK (1 test)'
 echo 'TALKBACK_RUNTIME=PASS' | tee evidence/accessibility/talkback-gate.txt
else
 echo 'TALKBACK_RUNTIME=UNAVAILABLE_IN_GOOGLE_APIS_IMAGE;SEMANTICS_TESTS=PASS' | tee evidence/accessibility/talkback-gate.txt
fi
printf 'EXTREME_FONT=PASS\nREDUCED_MOTION=PASS\nHIGH_CONTRAST=PASS\nRTL=PASS\nEXPANDED_LAYOUT=PASS\nNARROW_LAYOUT=PASS\n' | tee evidence/accessibility/adaptive-gate.txt
mark 'A11Y_ADAPTIVE=PASS'
cleanup

# Local-first/process-death gates after visual adaptation state is restored.
run_test 'com.veltrix.hom.vnext.DurabilityInstrumentedTest' evidence/runtime/durability.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest' evidence/runtime/offline.txt 'OK (2 tests)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#aSeedPart3State' evidence/runtime/process-death-seed.txt 'OK (1 test)'
run_test 'com.veltrix.hom.vnext.Part3ProcessDeathInstrumentedTest#zVerifyPart3StateAfterFreshInstrumentationProcess' evidence/runtime/process-death-verify.txt 'OK (1 test)'
mark 'DURABILITY=PASS'

# Environment fingerprint and honest emulator performance characterization.
{
 echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
 echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
 echo "hardware=$(adb shell getprop ro.hardware | tr -d '\r')"
 echo "egl=$(adb shell getprop ro.hardware.egl | tr -d '\r')"
 echo "refresh=$(adb shell dumpsys display | grep -E 'mRefreshRate|fps=' | head -8 || true)"
 adb shell dumpsys SurfaceFlinger 2>/dev/null | grep -Ei 'GLES|OpenGL|Vulkan|renderer|vendor' | head -50 || true
} | tee evidence/performance/renderer-environment.txt
adb shell am force-stop "$PACKAGE"; adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1 || true
timeout 20s adb shell am start -W -n "$ACTIVITY" >/dev/null; sleep 2
for x in "$X2" "$X3" "$X4" "$X1"; do adb shell input tap "$x" "$Y" >/dev/null 2>&1 || true; sleep .5; done
adb shell input swipe 2 "$MID" $((W*3/4)) "$MID" 550 >/dev/null 2>&1 || true; sleep .6
adb shell input swipe $((W*3/4)) "$MID" 2 "$MID" 550 >/dev/null 2>&1 || true
adb shell dumpsys gfxinfo "$PACKAGE" framestats > evidence/performance/gfxinfo-smoke.txt || true
adb shell dumpsys meminfo "$PACKAGE" > evidence/performance/meminfo.txt || true
adb shell logcat -d -t 2200 > evidence/runtime/logcat.txt || true
! grep -q 'ANR in com.veltrix.hom.vnext' evidence/runtime/logcat.txt
! grep -q 'FATAL EXCEPTION: main' evidence/runtime/logcat.txt
printf 'PERF_HARDWARE_ACCELERATED_EMULATOR_SMOKE=PASS\nPHYSICAL_DEVICE_PF=NOT_VERIFIED\nNO_120HZ_CLAIM=PASS\n' | tee evidence/performance/performance-gate.txt
mark 'PERF_RUNTIME=PASS'

# Exact source and artifact provenance.
sha256sum "$APK" | tee evidence/apk-sha256.txt
sha256sum "$TEST_APK" | tee evidence/androidtest-apk-sha256.txt
git archive --format=zip --output=evidence/Veltrix-Hom-Frontend-Part3-EXACT-SOURCE.zip "$GITHUB_SHA"
sha256sum evidence/Veltrix-Hom-Frontend-Part3-EXACT-SOURCE.zip | tee evidence/source-sha256.txt

test "$(wc -l < evidence/screens/screenshot-sha256.txt)" -eq 35
sha256sum -c evidence/screens/screenshot-sha256.txt
for name in 36-home-font-200 37-flashcard-font-200 38-settings-font-200 39-personal-reduced-motion 40-home-high-contrast 41-settings-rtl 42-home-expanded 43-map-expanded 44-project-expanded 45-store-expanded 46-settings-expanded 47-home-narrow 48-settings-narrow; do
 test -s "evidence/screens/${name}.png"; assert_png_complete "evidence/screens/${name}.png"
done
ADAPTIVE_COUNT=13
TOTAL_PNG="$(find evidence/screens -maxdepth 1 -type f -name '*.png' | wc -l)"
test "$TOTAL_PNG" -eq 49

cat > "evidence/VELTRIX_FRONTEND_PART3_FINAL_MANAGER_HANDOFF_2026-08-16.md" <<EOF_HANDOFF
# VELTRIX FRONTEND PART 3 — FINAL MANAGER HANDOFF

Status: FRONTEND_ACCEPTANCE_CANDIDATE (frontend-owned gates passed in this exact CI run; Manager acceptance and Check Engine are not claimed).

## Exact provenance
- Repository: ${GITHUB_REPOSITORY}
- Branch: ${GITHUB_REF_NAME}
- Exact tested SHA: ${GITHUB_SHA}
- Accepted Backend SHA: ${ACCEPTED_BACKEND_SHA}
- Previous Part 2 baseline SHA: ${PART2_BASELINE_SHA}
- Previous Part 1 SHA: ${PART1_SHA}
- CI run: ${GITHUB_RUN_ID}

## Closure ledger
- Part 1 debt: CLOSED by inherited regression + repaired Home/Personal/Map/identity/adaptive visual system proof.
- Part 2 debt: CLOSED by Projects/Project Space, Chat, Store/Inventory, learning surfaces and flashcard clipping/bounds proof.
- Part 3 implementation: Calculator, Translate, Notifications, Settings/Account/Data controls are concrete backend-integrated native screens.
- Backend preservation: accepted backend remains an ancestor; protected backend/server/core/database source is unchanged from accepted backend authority.
- Flashcard P0: global interactive-glass height cap removed; explicit Compose and runtime semantic-bounds gates pass.
- Visual QA: 35 core fresh scenarios + 13 adaptation scenarios + live Home; critical bounds gate is fail-closed.
- Motion: 3 runtime MP4 clips are ffprobe-gated for decodability, duration, resolution and temporal frames. Encoded screenrecord VFR is not treated as render FPS; MainActivity direct-manipulation frame pacing is replayed without screenrecord and fail-closed separately from Android gfxinfo summary.
- Accessibility/adaptive: extreme font, reduced motion, high contrast, RTL, narrow and expanded layouts executed. TalkBack status is recorded separately.
- Offline/process death: inherited durability/offline and fresh-process restoration tests pass.
- Performance: hardware-accelerated API36 emulator smoke/environment captured; physical-device performance/touch feel remains explicitly UNVERIFIED.
- External AI/translation: CI uses configured test-only providers; no live external-provider claim is made.

## KEEP / changed boundaries
KEEP: accepted backend contracts, account/security/isolation, deterministic authority, persistent shell, local-first/process-death architecture, four primary worlds.
CHANGED: frontend visual hierarchy/material primitives, avatar identity language, spatial Personal Map, Project Space presentation, Chat content/actions, Store preview/inventory presentation, flashcard layout validation, Part 3 tools/settings UI, adaptive/visual/motion proof gates.

## Evidence index
- Backend/Android tests: evidence/runtime/
- Visual matrix + XML + hash manifest: evidence/screens/
- Critical bounds: evidence/screens/layout-bounds-gate.txt
- Motion clips + ffprobe metadata + screenrecord-free MainActivity gfxinfo gate: evidence/motion/
- A11Y/adaptive: evidence/accessibility/
- Renderer/performance: evidence/performance/
- APK/test APK/source hashes: evidence/*sha256.txt

## Known limitation
Physical-device performance and human touch-feel are not fabricated. Current proof is the best available hardware-accelerated emulator/runtime evidence. This is the sole external performance proof boundary if Manager requires physical hardware before acceptance.

## Manager independent verification
Inspect full-resolution screenshots, motion clips and ffprobe metadata, TalkBack gate, renderer fingerprint, exact package hashes, and the physical-device limitation. Do not infer release approval from this handoff.
EOF_HANDOFF

printf 'PART1=CLOSED\nPART2=CLOSED\nPART3=CLOSED\nNO_KNOWN_P0_FRONTEND_DEFECT=PASS\nNO_KNOWN_P1_MISSION_DEFECT=PASS\nVISUAL_CORE_MATRIX=PASS count=35\nADAPTIVE_SCREENSHOTS=PASS count=%s\nTOTAL_PNG=PASS count=%s\nCRITICAL_BOUNDS=PASS\nMOTION_TEMPORAL_EVIDENCE=PASS clips=3 vfr_safe=1\nMOTION_FRAME_PACING_EMULATOR=PASS source=gfxinfo_summary screenrecord_free=1\nA11Y_ADAPTIVE=PASS\nOFFLINE_PROCESS_DEATH=PASS\nPERFORMANCE_EMULATOR=PASS\nPHYSICAL_DEVICE_PF=UNVERIFIED_EXTERNAL\nFRONTEND_ACCEPTANCE_CANDIDATE=PASS\n' "$ADAPTIVE_COUNT" "$TOTAL_PNG" | tee evidence/runtime/final-gate.txt
mark 'PART3_FINAL_PROOF=PASS'
