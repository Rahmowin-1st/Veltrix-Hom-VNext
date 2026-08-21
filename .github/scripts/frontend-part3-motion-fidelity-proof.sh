#!/usr/bin/env bash
set -euo pipefail

# Supplemental fail-closed high-fidelity motion proof.
# The primary Part 3 proof intentionally retains real runtime screenrecord clips, but Android
# screenrecord can be sparse/VFR. This gate proves meaningful intermediate production motion
# states frame-by-frame without relabeling duplicated/sparse frames as runtime FPS.
PACKAGE="com.veltrix.hom.vnext.dev"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$(find android/app/build/outputs/apk/androidTest -name '*androidTest.apk' -type f -size +0c | head -1)"
OUT="evidence/motion/high-fidelity"
REPORT="$OUT/deterministic-report.txt"
GATE="$OUT/high-fidelity-gate.txt"
HANDOFF="evidence/VELTRIX_FRONTEND_PART3_FINAL_MANAGER_HANDOFF_2026-08-16.md"

mkdir -p "$OUT/frames"
test -s "$APK"
test -s "$TEST_APK"
test -s "$HANDOFF"

# Same accepted backend runtime is still alive from the parent workflow step.
adb reverse tcp:8080 tcp:8080
adb install -r "$APK" >/dev/null
adb install -r "$TEST_APK" >/dev/null
I="$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')"
test -n "$I"

# Force normal motion for this proof. Reduced-motion behavior is already tested separately by the
# main proof script; this gate is specifically the production spring's intermediate-state evidence.
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true

timeout 210s adb shell am instrument -w \
  -e class 'com.veltrix.hom.vnext.MotionFidelityInstrumentedTest#primaryNavigationLensProducesFrameByFrameVisualSequence' \
  "$I" > "$OUT/instrumentation.txt"
cat "$OUT/instrumentation.txt"
grep -q 'OK (1 test)' "$OUT/instrumentation.txt"

adb exec-out run-as "$PACKAGE" cat files/motion-fidelity/report.txt > "$REPORT"
cat "$REPORT"
grep -q 'MOTION_DETERMINISTIC_VISUAL_SEQUENCE=PASS' "$REPORT"

for i in $(seq -f '%03g' 0 48); do
  frame="$OUT/frames/frame-${i}.jpg"
  adb exec-out run-as "$PACKAGE" cat "files/motion-fidelity/frame-${i}.jpg" > "$frame"
  test -s "$frame"
done
test "$(find "$OUT/frames" -maxdepth 1 -type f -name 'frame-*.jpg' | wc -l)" -eq 49
sha256sum "$OUT"/frames/frame-*.jpg > "$OUT/frame-sha256.txt"
sha256sum -c "$OUT/frame-sha256.txt"

# 60fps here is only deterministic playback cadence for the 49 independently rendered source
# frames. It is explicitly NOT a claim that the emulator/physical device rendered at 60fps.
ffmpeg -y -hide_banner -loglevel error -framerate 60 \
  -i "$OUT/frames/frame-%03d.jpg" \
  -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p \
  "$OUT/04-primary-nav-deterministic.mp4"
ffprobe -v error -count_frames -select_streams v:0 \
  -show_entries stream=avg_frame_rate,nb_read_frames,width,height \
  -show_entries format=duration -of json \
  "$OUT/04-primary-nav-deterministic.mp4" > "$OUT/deterministic-ffprobe.json"

python3 - "$REPORT" "$OUT/deterministic-ffprobe.json" <<'PY' | tee "$GATE"
import json
import re
import sys

report = open(sys.argv[1], encoding='utf-8').read()
meta = json.load(open(sys.argv[2], encoding='utf-8'))
stream = meta['streams'][0]

def frac(value):
    a, b = (value or '0/1').split('/')
    return float(a) / float(b) if float(b) else 0.0

frames = int(stream.get('nb_read_frames') or 0)
avg = frac(stream.get('avg_frame_rate'))
duration = float(meta['format']['duration'])
width = int(stream.get('width') or 0)
height = int(stream.get('height') or 0)
distinct_match = re.search(r'distinct_frames=(\d+)', report)
lens_match = re.search(r'distinct_lens_positions=(\d+)', report)
distinct = int(distinct_match.group(1)) if distinct_match else 0
lens_positions = int(lens_match.group(1)) if lens_match else 0

print(
    f'MOTION_DETERMINISTIC_ENCODED frames={frames} distinct_source_frames={distinct} '
    f'distinct_lens_positions={lens_positions} playback_fps={avg:.2f} '
    f'duration={duration:.3f}s size={width}x{height}'
)

if 'MOTION_DETERMINISTIC_VISUAL_SEQUENCE=PASS' not in report:
    raise SystemExit(48)
if frames != 49 or distinct < 12 or lens_positions < 12 or avg < 55 or width < 480 or height < 800:
    raise SystemExit(49)

print(
    'MOTION_HIGH_FIDELITY=PASS source=compose_main_test_clock source_frames=49 '
    'minimum_distinct=12 minimum_lens_positions=12 playback_fps=60 runtime_fps_claim=NONE'
)
print('MOTION_RUNTIME_PACING_SOURCE=jankstats_ui_cpu classification=SANITY_ONLY')
print('PHYSICAL_TOUCH_FEEL=NOT_VERIFIED')
PY

sha256sum "$OUT/04-primary-nav-deterministic.mp4" > "$OUT/video-sha256.txt"
grep -q 'MOTION_HIGH_FIDELITY=PASS source=compose_main_test_clock source_frames=49 minimum_distinct=12 minimum_lens_positions=12 playback_fps=60 runtime_fps_claim=NONE' "$GATE"

grep -q 'MOTION_FRAME_PACING_EMULATOR=PASS source=jankstats_ui_cpu screenrecord_free=1' evidence/motion/motion-gate.txt
grep -q 'JANKSTATS_UI_CPU_EMULATOR=PASS' evidence/performance/jankstats-ui-cpu.txt
cat "$GATE" >> evidence/motion/motion-gate.txt
printf '%s\n' 'MOTION_HIGH_FIDELITY=PASS source=compose_main_test_clock source_frames=49 minimum_distinct=12 minimum_lens_positions=12 playback_fps=60 runtime_fps_claim=NONE' >> evidence/runtime/final-gate.txt
printf '%s\n' 'MOTION_HIGH_FIDELITY=PASS' >> evidence/runtime/final-progress.txt

cat >> "$HANDOFF" <<'EOF_HANDOFF'

## High-fidelity motion closure addendum
- The three Android screenrecord MP4s remain real-runtime route/input context only; their sparse/VFR encoding is **not** treated as render FPS.
- A separate fail-closed production-motion gate captures **49 independently rendered frames** from the real `RootKineticBottomBar` `world-lens` spring by advancing Compose's manual test clock one frame at a time.
- Acceptance requires at least **12 distinct rendered source frames** and **12 distinct measured lens positions**, preventing duplicate-frame or endpoint-only slideshow evidence from passing.
- `04-primary-nav-deterministic.mp4` is encoded at **60fps playback cadence** from those source frames for review. This is deterministic visual playback evidence, **not a runtime-FPS claim**.
- MainActivity runtime pacing remains independently captured with screenrecord-free AndroidX JankStats and classified as CI-emulator sanity only. Physical-device performance/touch feel remains explicitly unverified.
EOF_HANDOFF

printf '%s\n' 'MOTION_FIDELITY_SUPPLEMENT=PASS'
