#!/usr/bin/env bash
set -euo pipefail

: "${GPU_MODE:?GPU_MODE required}"
: "${IMAGE_PACKAGE:?IMAGE_PACKAGE required}"
: "${ANDROID_AVD_HOME:?ANDROID_AVD_HOME required}"
APK="evidence/prior-v10/Veltrix-Hom-VNext-Part1-FINAL.apk"
TEST_APK="evidence/prior-v10/Veltrix-Hom-VNext-Part1-ANDROID-TEST.apk"
PKG="com.veltrix.hom.vnext.dev"
EXPECTED_ACTIVITY="com.veltrix.hom.vnext.MainActivity"
I=''
mkdir -p evidence/runtime

collect_diag() {
  adb devices -l > evidence/runtime/diag-adb-devices.txt 2>&1 || true
  adb shell getprop > evidence/runtime/diag-getprop.txt 2>&1 || true
  adb shell pm path "$PKG" > evidence/runtime/diag-package-path.txt 2>&1 || true
  adb shell pm list instrumentation > evidence/runtime/diag-instrumentation.txt 2>&1 || true
  adb shell dumpsys package "$PKG" > evidence/runtime/diag-package.txt 2>&1 || true
  adb shell dumpsys activity activities > evidence/runtime/diag-activity.txt 2>&1 || true
  adb shell dumpsys window windows > evidence/runtime/diag-window.txt 2>&1 || true
  adb logcat -d -b all -t 10000 > evidence/runtime/diag-logcat.txt 2>&1 || true
  ps -ef | grep -E '[e]mulator|[q]emu' > evidence/runtime/diag-host-processes.txt 2>&1 || true
  ss -ltnp > evidence/runtime/diag-host-listeners.txt 2>&1 || true
}
trap collect_diag EXIT

wait_framework() {
  local label="$1" stable=0 prev_s='' prev_f='' s f a p
  : > "evidence/runtime/framework-${label}.txt"
  for i in $(seq 1 180); do
    adb wait-for-device >/dev/null 2>&1 || true
    s=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    f=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    a=$(adb shell service check activity 2>&1 | tr -d '\r' || true)
    p=$(adb shell service check package 2>&1 | tr -d '\r' || true)
    printf 'sample=%s system=%s surface=%s activity=%s package=%s\n' "$i" "$s" "$f" "$a" "$p" | tee -a "evidence/runtime/framework-${label}.txt"
    if [ -n "$s" ] && [ -n "$f" ] && [ "$s" = "$prev_s" ] && [ "$f" = "$prev_f" ] && echo "$a" | grep -q found && echo "$p" | grep -q found; then stable=$((stable+1)); else stable=0; fi
    prev_s="$s"; prev_f="$f"
    [ "$stable" -ge 4 ] && return 0
    sleep 2
  done
  return 1
}

install_one() {
  local label="$1" file="$2" attempt before_s before_f after_s after_f out rc
  for attempt in $(seq 1 8); do
    wait_framework "${label}-pre-${attempt}"
    before_s=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    before_f=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    set +e
    out=$(timeout 120s adb install -r -t "$file" 2>&1); rc=$?
    set -e
    printf 'attempt=%s rc=%s before_system=%s before_surface=%s\n%s\n' "$attempt" "$rc" "$before_s" "$before_f" "$out" | tee "evidence/runtime/install-${label}-attempt-${attempt}.txt"
    after_s=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    after_f=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    if [ "$rc" -eq 0 ] && echo "$out" | grep -q Success; then
      cp "evidence/runtime/install-${label}-attempt-${attempt}.txt" "evidence/runtime/install-${label}.txt"
      return 0
    fi
    if [ "$before_s" != "$after_s" ] || [ "$before_f" != "$after_f" ] || echo "$out" | grep -Eqi 'broken pipe|dead object|device offline|transport|closed'; then
      echo "TRANSIENT_INSTALL_RETRY label=$label attempt=$attempt" | tee -a "evidence/runtime/install-${label}-retries.txt"
      continue
    fi
    return 1
  done
  return 1
}

resolve_launcher() {
  local label="$1" out
  wait_framework "resolve-${label}"
  adb shell pm path "$PKG" | tr -d '\r' | tee "evidence/runtime/package-${label}.txt"
  grep -q '^package:' "evidence/runtime/package-${label}.txt"
  adb shell dumpsys package "$PKG" > "evidence/runtime/dumpsys-package-${label}.txt"
  grep -q "$EXPECTED_ACTIVITY" "evidence/runtime/dumpsys-package-${label}.txt"
  out=$(adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$PKG" 2>&1 | tr -d '\r' || true)
  printf '%s\n' "$out" | tee "evidence/runtime/resolve-${label}.txt"
  echo "$out" | grep -q "$PKG/"
  echo "$out" | grep -q "$EXPECTED_ACTIVITY"
  RESOLVED_COMPONENT=$(printf '%s\n' "$out" | grep "$PKG/" | tail -1)
  test -n "$RESOLVED_COMPONENT"
}

refresh_instrumentation() {
  I=$(adb shell pm list instrumentation 2>/dev/null | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r' || true)
  test -n "$I"
  echo "$I" | tee evidence/runtime/instrumentation.txt
}

ensure_registered() {
  local label="$1"
  wait_framework "registered-${label}"
  if ! adb shell pm path "$PKG" 2>/dev/null | grep -q '^package:'; then install_one "recovery-${label}-app" "$APK"; fi
  refresh_instrumentation || true
  if [ -z "${I:-}" ]; then install_one "recovery-${label}-test" "$TEST_APK"; refresh_instrumentation; fi
  resolve_launcher "$label"
}

launch_app() {
  local label="$1" attempt sb fb sa fa out rc pid state
  : > "evidence/runtime/launch-${label}.txt"
  for attempt in $(seq 1 6); do
    ensure_registered "${label}-${attempt}"
    sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    adb logcat -c || true
    adb shell am force-stop "$PKG" || true
    set +e
    out=$(timeout 30s adb shell am start --user 0 -W -n "$RESOLVED_COMPONENT" 2>&1); rc=$?
    set -e
    printf 'attempt=%s component=%s rc=%s system=%s surface=%s\n%s\n' "$attempt" "$RESOLVED_COMPONENT" "$rc" "$sb" "$fb" "$out" | tee -a "evidence/runtime/launch-${label}.txt"
    sleep 3
    sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ]; then
      echo "TRANSIENT_FRAMEWORK_RESTART attempt=$attempt system=$sb->$sa surface=$fb->$fa" | tee -a "evidence/runtime/launch-${label}.txt"
      adb logcat -d -b all -t 4000 > "evidence/runtime/launch-${label}-${attempt}-logcat.txt" 2>&1 || true
      continue
    fi
    pid=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
    adb shell dumpsys activity activities > "evidence/runtime/activity-${label}-${attempt}.txt" 2>&1 || true
    adb shell dumpsys window windows > "evidence/runtime/window-${label}-${attempt}.txt" 2>&1 || true
    state=$(cat "evidence/runtime/activity-${label}-${attempt}.txt" "evidence/runtime/window-${label}-${attempt}.txt")
    if [ "$rc" -eq 0 ] && [ -n "$pid" ] && echo "$state" | grep -q "$PKG"; then return 0; fi
    echo "APP_LAUNCH_FAILED_STABLE_FRAMEWORK attempt=$attempt pid=$pid" | tee -a "evidence/runtime/launch-${label}.txt"
    adb logcat -d -b all -t 4000 > "evidence/runtime/launch-${label}-${attempt}-logcat.txt" 2>&1 || true
    return 1
  done
  return 1
}

run_test() {
  local label="$1" marker="$2" classarg="$3" maxsec="$4" attempt sb fb sa fa out rc
  for attempt in $(seq 1 6); do
    ensure_registered "${label}-${attempt}"
    refresh_instrumentation
    sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    set +e
    if [ -n "$classarg" ]; then out=$(timeout "${maxsec}s" adb shell am instrument -w -e class "$classarg" "$I" 2>&1); else out=$(timeout "${maxsec}s" adb shell am instrument -w "$I" 2>&1); fi
    rc=$?
    set -e
    printf '%s\n' "$out" | tee "evidence/runtime/${label}-attempt-${attempt}.txt"
    sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    if [ "$rc" -eq 0 ] && echo "$out" | grep -Fq "$marker"; then cp "evidence/runtime/${label}-attempt-${attempt}.txt" "evidence/runtime/${label}.txt"; return 0; fi
    if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ] || echo "$out" | grep -Eqi 'broken pipe|dead object|device offline|transport|closed'; then
      echo "TRANSIENT_TEST_RETRY label=$label attempt=$attempt system=$sb->$sa surface=$fb->$fa" | tee -a "evidence/runtime/${label}-retries.txt"
      continue
    fi
    echo "TEST_FAILED_STABLE_FRAMEWORK label=$label attempt=$attempt rc=$rc" | tee -a "evidence/runtime/${label}-retries.txt"
    return 1
  done
  return 1
}

sudo chmod 666 /dev/kvm
test -r /dev/kvm && test -w /dev/kvm
mkdir -p "$ANDROID_AVD_HOME"
AVD="veltrix_v15_api37_${GPU_MODE}"
echo no | avdmanager create avd -n "$AVD" -k "$IMAGE_PACKAGE" -f
C="$ANDROID_AVD_HOME/${AVD}.avd/config.ini"
grep -q '^hw.ramSize=' "$C" && sed -i 's/^hw.ramSize=.*/hw.ramSize=4096/' "$C" || echo hw.ramSize=4096 >> "$C"
grep -q '^hw.cpu.ncore=' "$C" && sed -i 's/^hw.cpu.ncore=.*/hw.cpu.ncore=4/' "$C" || echo hw.cpu.ncore=4 >> "$C"
grep -q '^disk.dataPartition.size=' "$C" && sed -i 's/^disk.dataPartition.size=.*/disk.dataPartition.size=4G/' "$C" || echo disk.dataPartition.size=4G >> "$C"
echo '4a70f76e971ed11a58cd125da050bef23b61223aa148e353994b4912d69342da  '"$APK" | sha256sum -c -
echo 'a6afe3978740c081edc8b1b1a4de785150cb1e16d8017a3a4e54ff32fe8fcd49  '"$TEST_APK" | sha256sum -c -
adb kill-server || true
adb start-server
nohup "$ANDROID_HOME/emulator/emulator" @"$AVD" -port 5554 -memory 4096 -cores 4 -partition-size 4096 -no-window -gpu "$GPU_MODE" -feature -Vulkan -noaudio -no-boot-anim -no-snapshot -no-metrics -accel on > evidence/runtime/emulator.log 2>&1 &
EPID=$!
echo "$EPID" > evidence/runtime/emulator.pid
SERIAL=''
for i in $(seq 1 240); do
  kill -0 "$EPID" 2>/dev/null || break
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [ -n "$SERIAL" ] && break
  [ "$i" -ge 15 ] && adb connect 127.0.0.1:5555 >/dev/null 2>&1 || true
  sleep 2
done
test -n "$SERIAL"
export ANDROID_SERIAL="$SERIAL"
printf 'ANDROID_SERIAL=%s\nGPU_MODE=%s\nIMAGE_PACKAGE=%s\n' "$SERIAL" "$GPU_MODE" "$IMAGE_PACKAGE" | tee evidence/runtime/device.txt
boot=0
for i in $(seq 1 360); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then boot=1; break; fi
  kill -0 "$EPID" 2>/dev/null || break
  sleep 2
done
test "$boot" -eq 1
adb shell getprop ro.build.version.release | tr -d '\r' | tee evidence/runtime/android-release.txt
adb shell getprop ro.build.version.sdk | tr -d '\r' | tee evidence/runtime/android-api.txt
grep -Fx 17 evidence/runtime/android-release.txt
grep -Fx 37 evidence/runtime/android-api.txt
wait_framework boot
echo "V15_API37_AOSP_BOOT=PASS gpu=$GPU_MODE image=$IMAGE_PACKAGE" | tee evidence/runtime/boot-gate.txt

install_one app "$APK"
install_one test "$TEST_APK"
refresh_instrumentation
resolve_launcher initial
echo V15_LAUNCHER_RESOLVE=PASS | tee evidence/runtime/resolve-gate.txt
echo V15_INSTALL=PASS | tee evidence/runtime/install-gate.txt
launch_app cold
echo V15_COLD_LAUNCH=PASS | tee evidence/runtime/cold-gate.txt
run_test shell 'OK (1 test)' 'com.veltrix.hom.vnext.ShellInstrumentedTest' 150
echo V15_SHELL=PASS | tee evidence/runtime/shell-gate.txt

curl -fsS --max-time 3 http://127.0.0.1:8080/health | tee evidence/runtime/pre-server-health.json
grep -q '"status":"ok"' evidence/runtime/pre-server-health.json
adb reverse --remove tcp:8080 >/dev/null 2>&1 || true
adb reverse tcp:8080 tcp:8080
adb reverse --list | tee evidence/runtime/adb-reverse.txt
grep -Eq 'tcp:8080.*tcp:8080' evidence/runtime/adb-reverse.txt
run_test server-device 'OK (1 test)' 'com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest' 180
echo V15_SERVER_DEVICE=PASS | tee evidence/runtime/server-device-gate.txt

run_test process-seed 'OK (1 test)' 'com.veltrix.hom.vnext.PersistenceInstrumentedTest#aSeedProjectForProcessRestart' 150
adb shell am force-stop "$PKG"
test -z "$(adb shell pidof "$PKG" 2>/dev/null || true)"
run_test process-verify 'OK (1 test)' 'com.veltrix.hom.vnext.PersistenceInstrumentedTest#zVerifyProjectAfterProcessRestart' 150
echo V15_PROCESS_DEATH=PASS | tee evidence/runtime/process-gate.txt

ensure_registered before-offline
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
sleep 3
test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = 1
run_test offline-seed 'OK (1 test)' 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest#aSeedOfflineDurableState' 150
adb shell am force-stop "$PKG"
run_test offline-verify 'OK (1 test)' 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest#zVerifyOfflineDurableStateAfterProcessRestart' 150
echo V15_OFFLINE=PASS | tee evidence/runtime/offline-gate.txt
adb shell cmd connectivity airplane-mode disable || adb shell settings put global airplane_mode_on 0
adb shell svc wifi enable || true
sleep 3
ensure_registered after-network
adb reverse tcp:8080 tcp:8080
adb shell pm clear "$PKG" | tee evidence/runtime/full-clear.txt
grep -q Success evidence/runtime/full-clear.txt
refresh_instrumentation
run_test connected 'OK (6 tests)' '' 360
echo V15_CONNECTED_6_TESTS=PASS | tee evidence/runtime/connected-gate.txt
launch_app final
echo V15_FINAL_LAUNCH=PASS | tee evidence/runtime/final-launch-gate.txt
echo "V15_ANDROID_RUNTIME_ALL=PASS gpu=$GPU_MODE image=$IMAGE_PACKAGE" | tee evidence/runtime/runtime-all.txt
trap - EXIT
collect_diag
