#!/usr/bin/env bash
set -uo pipefail

evidence_dir="$GITHUB_WORKSPACE/android/app/build/outputs/instrumentation-evidence"
mkdir -p "$evidence_dir"

capture_evidence() {
  local exit_status="$1"
  printf 'exit_status=%s\n' "$exit_status" > "$evidence_dir/status.txt"
  timeout --kill-after=5s 30s adb exec-out screencap -p > "$evidence_dir/emulator-screen.png" || true
  timeout --kill-after=5s 30s adb logcat -d -v threadtime > "$evidence_dir/logcat.txt" || true
}

timeout --kill-after=10s 3m bash -c '
  set -o pipefail
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ] &&
    adb shell service check package 2>/dev/null | tr -d "\r" | grep -q "package: found"; do
    sleep 2
  done
'
android_ready_status=$?
if [ "$android_ready_status" -ne 0 ]; then
  capture_evidence "$android_ready_status"
  exit "$android_ready_status"
fi

cd "$GITHUB_WORKSPACE/android"
timeout --kill-after=30s 30m ./gradlew connectedDebugAndroidTest --no-daemon --stacktrace
test_status=$?
if [ "$test_status" -ne 0 ]; then
  capture_evidence "$test_status"
fi
exit "$test_status"
