#!/usr/bin/env bash
set -u

evidence_dir="$GITHUB_WORKSPACE/android/app/build/outputs/managed_device_android_test_additional_output"
status_file="$evidence_dir/evidence-status.txt"
device_evidence_dir="$evidence_dir/device-test-failures"
mkdir -p "$evidence_dir"
if [ ! -s "$status_file" ]; then
  printf 'capture_state=initialized\n' > "$status_file"
fi

record_status() {
  printf '%s\n' "$1" >> "$status_file"
}

record_tree_status() {
  local evidence_label="$1"
  local evidence_path="$2"
  local file_count=0
  if [ -d "$evidence_path" ]; then
    file_count="$(find "$evidence_path" -type f | wc -l | tr -d ' ')"
  fi
  if [ "$file_count" -gt 0 ]; then
    record_status "$evidence_label=available files=$file_count path=$evidence_path"
  else
    record_status "$evidence_label=missing files=0 path=$evidence_path"
  fi
}

record_matching_status() {
  local evidence_label="$1"
  local evidence_path="$2"
  local evidence_pattern="$3"
  local file_count=0
  if [ -d "$evidence_path" ]; then
    file_count="$(find "$evidence_path" -type f -name "$evidence_pattern" | wc -l | tr -d ' ')"
  fi
  if [ "$file_count" -gt 0 ]; then
    record_status "$evidence_label=available files=$file_count path=$evidence_path"
  else
    record_status "$evidence_label=missing files=0 path=$evidence_path"
  fi
}

capture_failure_evidence() {
  local failure_phase="$1"
  local original_status="$2"
  local screenshot_file="$evidence_dir/emulator-screen.png"
  local screenshot_status=0
  local logcat_file="$evidence_dir/logcat.txt"
  local logcat_status=0
  local pull_log="$evidence_dir/adb-pull.log"
  local pull_status=0
  local pulled_file_count

  record_status "failure_phase=$failure_phase"
  record_status "original_exit_status=$original_status"

  timeout --kill-after=5s 30s adb exec-out screencap -p > "$screenshot_file" || screenshot_status=$?
  if [ "$screenshot_status" -eq 0 ] && [ -s "$screenshot_file" ]; then
    record_status "emulator_screenshot_capture=success path=$screenshot_file"
  else
    record_status "emulator_screenshot_capture=failed exit_status=$screenshot_status path=$screenshot_file"
  fi

  timeout --kill-after=5s 30s adb logcat -d -v threadtime > "$logcat_file" || logcat_status=$?
  if [ "$logcat_status" -eq 0 ] && [ -s "$logcat_file" ]; then
    record_status "logcat_capture=success path=$logcat_file"
  else
    record_status "logcat_capture=failed exit_status=$logcat_status path=$logcat_file"
  fi

  mkdir -p "$device_evidence_dir"
  timeout --kill-after=5s 60s adb pull /sdcard/Android/data/com.dailybeat.app.qa/files/instrumentation-failure-evidence "$device_evidence_dir" > "$pull_log" 2>&1 || pull_status=$?
  pulled_file_count="$(find "$device_evidence_dir" -type f | wc -l | tr -d ' ')"
  if [ "$pull_status" -eq 0 ] && [ "$pulled_file_count" -gt 0 ]; then
    record_status "device_evidence_pull=success files=$pulled_file_count log=$pull_log"
  else
    record_status "device_evidence_pull=failed exit_status=$pull_status files=$pulled_file_count log=$pull_log"
  fi

  record_tree_status "connected_reports" "$GITHUB_WORKSPACE/android/app/build/reports/androidTests/connected"
  record_tree_status "raw_results" "$GITHUB_WORKSPACE/android/app/build/outputs/androidTest-results/connected"
  record_matching_status "screenshots" "$evidence_dir" "*.png"
  record_matching_status "stack_traces" "$device_evidence_dir" "*.txt"
  record_matching_status "logcat" "$evidence_dir" "logcat.txt"
  record_status "capture_state=complete"
}

fail_with_evidence() {
  local failure_phase="$1"
  local original_status="$2"
  local capture_status
  set +e
  capture_failure_evidence "$failure_phase" "$original_status"
  capture_status=$?
  if [ "$capture_status" -ne 0 ]; then
    printf 'capture_function=failed exit_status=%s\n' "$capture_status" >> "$status_file"
    printf 'Evidence capture failed with status %s\n' "$capture_status" >&2
  fi
  exit "$original_status"
}

record_status "emulator_readiness=started timeout=2m"
command_status=0
timeout --kill-after=10s 2m bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ]; do sleep 2; done' || command_status=$?
record_status "emulator_readiness_exit_status=$command_status"
if [ "$command_status" -ne 0 ]; then
  fail_with_evidence "emulator_readiness" "$command_status"
fi

record_status "package_readiness=started timeout=2m"
command_status=0
timeout --kill-after=10s 2m bash -c 'until adb shell pm list packages >/dev/null 2>&1; do sleep 2; done' || command_status=$?
record_status "package_readiness_exit_status=$command_status"
if [ "$command_status" -ne 0 ]; then
  fail_with_evidence "package_readiness" "$command_status"
fi

cd "$GITHUB_WORKSPACE/android"
record_status "connected_tests=started timeout=25m"
command_status=0
timeout --kill-after=30s 25m ./gradlew connectedDebugAndroidTest --no-daemon --stacktrace || command_status=$?
record_status "connected_tests_exit_status=$command_status"
if [ "$command_status" -ne 0 ]; then
  fail_with_evidence "connected_tests" "$command_status"
fi
