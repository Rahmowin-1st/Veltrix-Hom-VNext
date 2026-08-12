#!/usr/bin/env bash
set -euo pipefail

: "${GPU_MODE:?GPU_MODE required}"
: "${ANDROID_AVD_HOME:?ANDROID_AVD_HOME required}"
APK="evidence/prior-v10/Veltrix-Hom-VNext-Part1-FINAL.apk"
TEST_APK="evidence/prior-v10/Veltrix-Hom-VNext-Part1-ANDROID-TEST.apk"
PKG="com.veltrix.hom.vnext.dev"
CMP="com.veltrix.hom.vnext.dev/com.veltrix.hom.vnext.MainActivity"
mkdir -p evidence/runtime

collect_diag() {
  adb devices -l > evidence/runtime/diag-adb-devices.txt 2>&1 || true
  adb shell getprop > evidence/runtime/diag-getprop.txt 2>&1 || true
  adb shell dumpsys activity activities > evidence/runtime/diag-activity.txt 2>&1 || true
  adb shell dumpsys window windows > evidence/runtime/diag-window.txt 2>&1 || true
  adb logcat -d -b all -t 6000 > evidence/runtime/diag-logcat.txt 2>&1 || true
  ps -ef | grep -E '[e]mulator|[q]emu' > evidence/runtime/diag-host-processes.txt 2>&1 || true
  ss -ltnp > evidence/runtime/diag-host-listeners.txt 2>&1 || true
}
trap collect_diag EXIT

wait_framework() {
  local label="$1" stable=0 prev_s='' prev_f='' s f a p
  : > "evidence/runtime/framework-${label}.txt"
  for i in $(seq 1 120); do
    adb wait-for-device >/dev/null 2>&1 || true
    s=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    f=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    a=$(adb shell service check activity 2>&1 | tr -d '\r' || true)
    p=$(adb shell service check package 2>&1 | tr -d '\r' || true)
    printf 'sample=%s system=%s surface=%s activity=%s package=%s\n' "$i" "$s" "$f" "$a" "$p" | tee -a "evidence/runtime/framework-${label}.txt"
    if [ -n "$s" ] && [ -n "$f" ] && [ "$s" = "$prev_s" ] && [ "$f" = "$prev_f" ] && echo "$a" | grep -q found && echo "$p" | grep -q found; then stable=$((stable+1)); else stable=0; fi
    prev_s="$s"; prev_f="$f"
    [ "$stable" -ge 3 ] && return 0
    sleep 2
  done
  return 1
}

install_apk() {
  local label="$1" file="$2" attempt sb fb sa fa out rc
  for attempt in $(seq 1 8); do
    wait_framework "${label}-pre-${attempt}"
    sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    set +e
    out=$(timeout 90s adb install -r -t "$file" 2>&1)
    rc=$?
    set -e
    printf 'attempt=%s before_system=%s before_surface=%s rc=%s\n%s\n' "$attempt" "$sb" "$fb" "$rc" "$out" | tee "evidence/runtime/install-${label}-attempt-${attempt}.txt"
    sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    if [ "$rc" -eq 0 ] && echo "$out" | grep -q 'Success'; then
      cp "evidence/runtime/install-${label}-attempt-${attempt}.txt" "evidence/runtime/install-${label}.txt"
      wait_framework "${label}-post-${attempt}"
      return 0
    fi
    if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ] || echo "$out" | grep -Eqi 'broken pipe|dead object|device offline|closed|transport'; then
      echo "TRANSIENT_FRAMEWORK_RETRY label=$label attempt=$attempt system=$sb->$sa surface=$fb->$fa" | tee -a "evidence/runtime/install-${label}-retries.txt"
      sleep 2
      continue
    fi
    echo "INSTALL_FAILED_WITH_STABLE_FRAMEWORK label=$label attempt=$attempt rc=$rc" | tee -a "evidence/runtime/install-${label}-retries.txt"
    return 1
  done
  return 1
}

launch_verified() {
  local label="$1" attempt sb fb sa fa pid state out
  : > "evidence/runtime/launch-${label}.txt"
  for attempt in $(seq 1 8); do
    wait_framework "${label}-pre-${attempt}"
    sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    adb logcat -c || true
    adb shell am force-stop "$PKG" || true
    out=$(timeout 20s adb shell am start -W -n "$CMP" 2>&1 || true)
    printf 'attempt=%s before_system=%s before_surface=%s\n%s\n' "$attempt" "$sb" "$fb" "$out" | tee -a "evidence/runtime/launch-${label}.txt"
    for poll in $(seq 1 12); do
      sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
      fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
      if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ]; then
        echo "FRAMEWORK_RESTART attempt=$attempt poll=$poll system=$sb->$sa surface=$fb->$fa" | tee -a "evidence/runtime/launch-${label}.txt"
        break
      fi
      pid=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
      adb shell dumpsys activity activities > "evidence/runtime/activity-${label}-${attempt}.txt" 2>&1 || true
      adb shell dumpsys window windows > "evidence/runtime/window-${label}-${attempt}.txt" 2>&1 || true
      state=$( { grep -m1 -E 'mResumedActivity|topResumedActivity|ResumedActivity' "evidence/runtime/activity-${label}-${attempt}.txt" || true; grep -m1 -E 'mCurrentFocus|mFocusedApp' "evidence/runtime/window-${label}-${attempt}.txt" || true; } | tr '\n' ' ' )
      printf 'attempt=%s poll=%s app_pid=%s state=%s\n' "$attempt" "$poll" "$pid" "$state" | tee -a "evidence/runtime/launch-${label}.txt"
      if [ -n "$pid" ] && echo "$state" | grep -q "$PKG"; then return 0; fi
      sleep 1
    done
    sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    adb logcat -d -b all -t 2500 > "evidence/runtime/launch-${label}-${attempt}-logcat.txt" 2>&1 || true
    if [ "$sa" = "$sb" ] && [ "$fa" = "$fb" ]; then
      echo "APP_LAUNCH_FAILED_WITH_STABLE_FRAMEWORK attempt=$attempt" | tee -a "evidence/runtime/launch-${label}.txt"
      return 1
    fi
  done
  return 1
}

run_test() {
  local label="$1" marker="$2" classarg="$3" max_seconds="$4" attempt sb fb sa fa out rc
  for attempt in $(seq 1 6); do
    wait_framework "${label}-pre-${attempt}"
    sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    set +e
    if [ -n "$classarg" ]; then out=$(timeout "${max_seconds}s" adb shell am instrument -w -e class "$classarg" "$I" 2>&1); else out=$(timeout "${max_seconds}s" adb shell am instrument -w "$I" 2>&1); fi
    rc=$?
    set -e
    printf '%s\n' "$out" | tee "evidence/runtime/${label}-attempt-${attempt}.txt"
    sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
    if [ "$rc" -eq 0 ] && echo "$out" | grep -Fq "$marker"; then cp "evidence/runtime/${label}-attempt-${attempt}.txt" "evidence/runtime/${label}.txt"; return 0; fi
    if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ] || echo "$out" | grep -Eqi 'broken pipe|dead object|device offline|closed|transport'; then
      echo "TRANSIENT_FRAMEWORK_RETRY label=$label attempt=$attempt system=$sb->$sa surface=$fb->$fa" | tee -a "evidence/runtime/${label}-retries.txt"
      continue
    fi
    echo "TEST_FAILED_WITH_STABLE_FRAMEWORK label=$label attempt=$attempt rc=$rc" | tee -a "evidence/runtime/${label}-retries.txt"
    return 1
  done
  return 1
}

sudo chmod 666 /dev/kvm
test -r /dev/kvm && test -w /dev/kvm
mkdir -p "$ANDROID_AVD_HOME"
echo no | avdmanager create avd -n veltrix_v13_api37 -k 'system-images;android-37.0;google_apis;x86_64' -f
C="$ANDROID_AVD_HOME/veltrix_v13_api37.avd/config.ini"
grep -q '^hw.ramSize=' "$C" && sed -i 's/^hw.ramSize=.*/hw.ramSize=4096/' "$C" || echo hw.ramSize=4096 >> "$C"
grep -q '^hw.cpu.ncore=' "$C" && sed -i 's/^hw.cpu.ncore=.*/hw.cpu.ncore=4/' "$C" || echo hw.cpu.ncore=4 >> "$C"
grep -q '^disk.dataPartition.size=' "$C" && sed -i 's/^disk.dataPartition.size=.*/disk.dataPartition.size=4G/' "$C" || echo disk.dataPartition.size=4G >> "$C"
adb kill-server || true
adb start-server
nohup "$ANDROID_HOME/emulator/emulator" @veltrix_v13_api37 -port 5554 -memory 4096 -cores 4 -partition-size 4096 -no-window -gpu "$GPU_MODE" -feature -Vulkan -noaudio -no-boot-anim -no-snapshot -no-metrics -accel on > evidence/runtime/emulator.log 2>&1 &
EPID=$!
echo "$EPID" > evidence/runtime/emulator.pid
SERIAL=''
for i in $(seq 1 180); do
  kill -0 "$EPID" 2>/dev/null || break
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [ -n "$SERIAL" ] && break
  [ "$i" -ge 15 ] && adb connect 127.0.0.1:5555 >/dev/null 2>&1 || true
  sleep 2
done
test -n "$SERIAL"
export ANDROID_SERIAL="$SERIAL"
printf 'ANDROID_SERIAL=%s\nGPU_MODE=%s\n' "$SERIAL" "$GPU_MODE" | tee evidence/runtime/device.txt
for i in $(seq 1 180); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break || sleep 2; done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1
adb shell getprop ro.build.version.release | tr -d '\r' | tee evidence/runtime/android-release.txt
adb shell getprop ro.build.version.sdk | tr -d '\r' | tee evidence/runtime/android-api.txt
grep -Fx 17 evidence/runtime/android-release.txt
grep -Fx 37 evidence/runtime/android-api.txt
AVAIL=$(adb shell df -Pk /data | awk 'NR==2 {print $4}' | tr -d '\r')
echo "DATA_AVAILABLE_KB=$AVAIL" | tee evidence/runtime/data.txt
test "$AVAIL" -ge 524288
wait_framework boot
echo "V13_API37_BOOT=PASS gpu=$GPU_MODE" | tee evidence/runtime/boot-gate.txt

install_apk app "$APK"
install_apk test "$TEST_APK"
adb shell pm path "$PKG" | tee evidence/runtime/package-path.txt
grep -q '^package:' evidence/runtime/package-path.txt
I=$(adb shell pm list instrumentation | sed -n 's/^instrumentation:\([^ ]*\).*target=com.veltrix.hom.vnext.dev.*/\1/p' | head -1 | tr -d '\r')
test -n "$I"
echo "$I" | tee evidence/runtime/instrumentation.txt
echo V13_INSTALL=PASS | tee evidence/runtime/install-gate.txt

launch_verified cold
echo V13_COLD_LAUNCH=PASS | tee evidence/runtime/cold-gate.txt
run_test shell 'OK (1 test)' 'com.veltrix.hom.vnext.ShellInstrumentedTest' 120
echo V13_SHELL=PASS | tee evidence/runtime/shell-gate.txt

curl -fsS --max-time 3 http://127.0.0.1:8080/health | tee evidence/runtime/pre-server-health.json
grep -q '"status":"ok"' evidence/runtime/pre-server-health.json
adb reverse --remove tcp:8080 >/dev/null 2>&1 || true
adb reverse tcp:8080 tcp:8080
adb reverse --list | tee evidence/runtime/adb-reverse.txt
grep -Eq 'tcp:8080.*tcp:8080' evidence/runtime/adb-reverse.txt
run_test server-device 'OK (1 test)' 'com.veltrix.hom.vnext.ServerIntegrationInstrumentedTest' 150
echo V13_SERVER_DEVICE=PASS | tee evidence/runtime/server-device-gate.txt

run_test process-seed 'OK (1 test)' 'com.veltrix.hom.vnext.PersistenceInstrumentedTest#aSeedProjectForProcessRestart' 120
adb shell am force-stop "$PKG"
test -z "$(adb shell pidof "$PKG" 2>/dev/null || true)"
run_test process-verify 'OK (1 test)' 'com.veltrix.hom.vnext.PersistenceInstrumentedTest#zVerifyProjectAfterProcessRestart' 120
echo V13_PROCESS_DEATH=PASS | tee evidence/runtime/process-gate.txt

wait_framework before-offline
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
sleep 2
test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = 1
run_test offline-seed 'OK (1 test)' 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest#aSeedOfflineDurableState' 120
adb shell am force-stop "$PKG"
run_test offline-verify 'OK (1 test)' 'com.veltrix.hom.vnext.OfflineDataInstrumentedTest#zVerifyOfflineDurableStateAfterProcessRestart' 120
echo V13_OFFLINE=PASS | tee evidence/runtime/offline-gate.txt
adb shell cmd connectivity airplane-mode disable || adb shell settings put global airplane_mode_on 0
adb shell svc wifi enable || true
for i in $(seq 1 30); do [ "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = 0 ] && break || sleep 1; done
wait_framework after-network-restore
adb reverse tcp:8080 tcp:8080

full_ok=0
for attempt in $(seq 1 5); do
  wait_framework "full-pre-${attempt}"
  adb shell pm clear "$PKG" | tee "evidence/runtime/full-clear-${attempt}.txt"
  grep -q Success "evidence/runtime/full-clear-${attempt}.txt"
  adb reverse tcp:8080 tcp:8080
  sb=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
  fb=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
  set +e
  out=$(timeout 300s adb shell am instrument -w "$I" 2>&1)
  rc=$?
  set -e
  printf '%s\n' "$out" | tee "evidence/runtime/full-connected-attempt-${attempt}.txt"
  sa=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
  fa=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
  if [ "$rc" -eq 0 ] && echo "$out" | grep -Fq 'OK (6 tests)'; then cp "evidence/runtime/full-connected-attempt-${attempt}.txt" evidence/runtime/connected.txt; full_ok=1; break; fi
  if [ "$sa" != "$sb" ] || [ "$fa" != "$fb" ] || echo "$out" | grep -Eqi 'broken pipe|dead object|device offline|closed|transport'; then echo "TRANSIENT_FRAMEWORK_RETRY full attempt=$attempt system=$sb->$sa surface=$fb->$fa" | tee -a evidence/runtime/full-retries.txt; continue; fi
  echo "FULL_SUITE_FAILED_WITH_STABLE_FRAMEWORK attempt=$attempt rc=$rc" | tee -a evidence/runtime/full-retries.txt
  break
done
test "$full_ok" -eq 1
echo V13_CONNECTED_6_TESTS=PASS | tee evidence/runtime/connected-gate.txt
launch_verified final
echo V13_FINAL_LAUNCH=PASS | tee evidence/runtime/final-launch-gate.txt
echo "V13_ANDROID_RUNTIME_ALL=PASS gpu=$GPU_MODE" | tee evidence/runtime/runtime-all.txt
trap - EXIT
collect_diag
