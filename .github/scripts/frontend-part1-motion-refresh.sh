#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence/motion evidence/diagnostics
PACKAGE="com.veltrix.hom.vnext.dev"
EVIDENCE_ACTIVITY="$PACKAGE/com.veltrix.hom.vnext.FrontendEvidenceActivity"

fresh_dump() {
  local out="$1"
  rm -f "$out"
  adb shell rm -f /sdcard/veltrix-motion.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump /sdcard/veltrix-motion.xml >/dev/null 2>&1
  adb pull /sdcard/veltrix-motion.xml "$out" >/dev/null 2>&1
  test -s "$out"
}

wait_loaded_home() {
  local out="evidence/diagnostics/motion-home-ui.xml"
  for _ in $(seq 1 30); do
    if fresh_dump "$out" && grep -Eqi 'Continue with Veltrix|Retest Newton' "$out"; then
      sleep .5
      return 0
    fi
    sleep .4
  done
  echo 'Loaded premium Home fixture did not become ready' >&2
  return 1
}

node_center() {
  local needle="$1" file="evidence/diagnostics/motion-node-ui.xml"
  fresh_dump "$file"
  python3 - "$needle" "$file" <<'PY'
import re,sys,xml.etree.ElementTree as ET
needle=sys.argv[1].strip().lower(); path=sys.argv[2]
root=ET.parse(path).getroot()
for n in root.iter('node'):
    vals=(n.attrib.get('text','').strip().lower(), n.attrib.get('content-desc','').strip().lower())
    if not any(needle in v for v in vals): continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups())
        print((x1+x2)//2, (y1+y2)//2)
        sys.exit(0)
sys.exit(1)
PY
}

# Use the debug-only fixture so business callbacks are inert: the clip can show a
# true press/release deformation on the exact production premium Home surface
# without navigating away or recording launcher/boot frames.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$EVIDENCE_ACTIVITY" --es scenario HOME_FOCUS >/dev/null
wait_loaded_home
CENTER="$(node_center 'Continue with Veltrix')"
test -n "$CENTER"
set -- $CENTER
X="$1"; Y="$2"

adb shell rm -f /sdcard/home-primary-loaded.mp4 >/dev/null 2>&1 || true
adb shell screenrecord --time-limit 5 /sdcard/home-primary-loaded.mp4 >/dev/null 2>&1 &
P=$!
sleep .7
adb shell input motionevent DOWN "$X" "$Y"
sleep .45
adb shell input motionevent UP "$X" "$Y"
sleep 1
adb shell input motionevent DOWN "$X" "$Y"
sleep .18
adb shell input motionevent UP "$X" "$Y"
sleep 1
wait "$P" || true
adb pull /sdcard/home-primary-loaded.mp4 evidence/motion/home-avatar-primary-glass.mp4 >/dev/null
test -s evidence/motion/home-avatar-primary-glass.mp4

echo 'HOME_PRIMARY_GLASS_LOADED_PRESS=PASS' | tee evidence/motion/home-primary-glass-gate.txt

# Replace the old overclaim: this clip proves the loaded premium Home glass
# press/release. Static identity/world appearance is covered by the screenshot matrix.
cat > evidence/motion-evidence-matrix.txt <<'MOTION'
bottom navigation transition = motion/navigation-destinations.mp4
sidebar direct open/close = motion/sidebar-direct-manipulation.mp4
Home premium primary glass press/release on loaded world = motion/home-avatar-primary-glass.mp4
Personal Map spatial exploration/scroll = motion/personal-map-exploration.mp4
Reduced Motion navigation = motion/reduced-motion-navigation.mp4
Home→Personal shared element = NOT IMPLEMENTED; no false shared-transition claim
major Map unlock/reward = NOT CAPTURED; no authoritative unlock mutation was executed
MOTION
