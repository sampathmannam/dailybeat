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

instrumentation_args=()
if [ -n "${DAILYBEAT_BACKUP_TEST_EMAIL:-}" ] && [ -n "${DAILYBEAT_BACKUP_TEST_PASSWORD:-}" ]; then
  if [ -z "${SUPABASE_URL:-}" ] || [ -z "${SUPABASE_ANON_KEY:-}" ]; then
    echo "Live backup credentials were provided without Supabase configuration."
    capture_evidence 2
    exit 2
  fi

  sha256_text() {
    printf '%s' "$1" | sha256sum | awk '{print $1}'
  }
  instrumentation_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.backupEmail=$DAILYBEAT_BACKUP_TEST_EMAIL"
    "-Pandroid.testInstrumentationRunnerArguments.backupPassword=$DAILYBEAT_BACKUP_TEST_PASSWORD"
    "-Pandroid.testInstrumentationRunnerArguments.backupEmailSha=$(sha256_text "$DAILYBEAT_BACKUP_TEST_EMAIL")"
    "-Pandroid.testInstrumentationRunnerArguments.backupPasswordSha=$(sha256_text "$DAILYBEAT_BACKUP_TEST_PASSWORD")"
    "-Pandroid.testInstrumentationRunnerArguments.backupConfigSha=$(sha256_text "$SUPABASE_URL|$SUPABASE_ANON_KEY")"
  )
elif [ "${DAILYBEAT_REQUIRE_LIVE_BACKUP:-0}" = "1" ]; then
  echo "Live backup verification requires DAILYBEAT_BACKUP_TEST_EMAIL and DAILYBEAT_BACKUP_TEST_PASSWORD."
  capture_evidence 2
  exit 2
fi

timeout --kill-after=30s 30m ./gradlew connectedDebugAndroidTest \
  "${instrumentation_args[@]}" --no-daemon --stacktrace
test_status=$?
capture_evidence "$test_status"
exit "$test_status"
