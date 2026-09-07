#!/usr/bin/env bash
# Build and exercise the isolated DailyBeat QA app on one connected Android phone.
# Usage: ./scripts/mac_phone_e2e.sh [adb-serial]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

if [ -n "${DAILYBEAT_BRANCH:-}" ]; then
  echo "=== Sync branch $DAILYBEAT_BRANCH ==="
  git -C "$ROOT" fetch origin "$DAILYBEAT_BRANCH"
  git -C "$ROOT" checkout "$DAILYBEAT_BRANCH"
  git -C "$ROOT" pull --ff-only origin "$DAILYBEAT_BRANCH"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Add Android SDK platform-tools to PATH."
  exit 1
fi

mac_adb start-server
mac_adb_pick_device "${1:-}"
mac_ensure_java

case "$MAC_ADB_SERIAL" in
  emulator-*)
    echo "This gate requires a physical Android phone; $MAC_ADB_SERIAL is an emulator."
    echo "Connect the phone with USB debugging enabled, then pass its serial as the argument:"
    echo "  ./scripts/mac_phone_e2e.sh YOUR_PHONE_SERIAL"
    exit 1
    ;;
esac

QA_PACKAGE="com.dailybeat.app.qa"
QA_TEST_PACKAGE="${QA_PACKAGE}.test"
for disposable_package in "$QA_TEST_PACKAGE" "$QA_PACKAGE"; do
  package_path="$(mac_adb shell pm path "$disposable_package" 2>/dev/null || true)"
  if [[ "$package_path" == package:* ]]; then
    echo "Removing disposable QA package $disposable_package from $MAC_ADB_SERIAL"
    if ! mac_adb uninstall "$disposable_package" >/dev/null; then
      echo "Could not remove $disposable_package. The stable com.dailybeat.app package was not touched."
      exit 1
    fi
  fi
done

echo "=== Build, unit tests, and lint for QA on $MAC_ADB_SERIAL ==="
cd "$ROOT/android"
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --stacktrace

echo "=== Run Compose end-to-end tests on $MAC_ADB_SERIAL ==="
phone_instrumentation_args=()
if [ -n "${DAILYBEAT_BACKUP_TEST_EMAIL:-}" ] && [ -n "${DAILYBEAT_BACKUP_TEST_PASSWORD:-}" ]; then
  if [ -z "${SUPABASE_URL:-}" ] || [ -z "${SUPABASE_ANON_KEY:-}" ]; then
    echo "Live backup credentials were provided without SUPABASE_URL and SUPABASE_ANON_KEY."
    exit 1
  fi

  mac_sha256_text() {
    printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
  }
  phone_instrumentation_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.backupEmail=$DAILYBEAT_BACKUP_TEST_EMAIL"
    "-Pandroid.testInstrumentationRunnerArguments.backupPassword=$DAILYBEAT_BACKUP_TEST_PASSWORD"
    "-Pandroid.testInstrumentationRunnerArguments.backupEmailSha=$(mac_sha256_text "$DAILYBEAT_BACKUP_TEST_EMAIL")"
    "-Pandroid.testInstrumentationRunnerArguments.backupPasswordSha=$(mac_sha256_text "$DAILYBEAT_BACKUP_TEST_PASSWORD")"
    "-Pandroid.testInstrumentationRunnerArguments.backupConfigSha=$(mac_sha256_text "$SUPABASE_URL|$SUPABASE_ANON_KEY")"
  )
elif [ "${DAILYBEAT_REQUIRE_LIVE_BACKUP:-0}" = "1" ]; then
  echo "Live backup verification requires DAILYBEAT_BACKUP_TEST_EMAIL and DAILYBEAT_BACKUP_TEST_PASSWORD."
  exit 1
fi

./gradlew connectedDebugAndroidTest "${phone_instrumentation_args[@]}" --no-daemon --stacktrace

echo "=== Reinstall QA app after the Android test runner cleanup ==="
./gradlew installDebug --no-daemon --stacktrace

mac_adb shell pm grant "$QA_PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

PACKAGE_PATH="$(mac_adb shell pm path "$QA_PACKAGE" 2>/dev/null || true)"
if [[ "$PACKAGE_PATH" != package:* ]]; then
  echo "QA package was not installed on $MAC_ADB_SERIAL after instrumentation."
  exit 1
fi

echo "=== Launch QA app and capture evidence ==="
EVIDENCE_DIR="$ROOT/android/app/build/outputs/phone-evidence/$MAC_ADB_SERIAL"
mkdir -p "$EVIDENCE_DIR"
mac_adb logcat -c
mac_adb shell am force-stop "$QA_PACKAGE"
mac_adb shell getprop ro.build.fingerprint > "$EVIDENCE_DIR/device-build.txt"
mac_adb shell dumpsys package "$QA_PACKAGE" > "$EVIDENCE_DIR/package.txt"
if ! mac_adb shell am start -W -n "$QA_PACKAGE/com.dailybeat.app.MainActivity" \
  > "$EVIDENCE_DIR/launch.txt" 2>&1; then
  echo "Android could not launch the QA activity:"
  cat "$EVIDENCE_DIR/launch.txt"
  exit 1
fi
sleep 3
mac_adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png"
mac_adb logcat -d -v threadtime > "$EVIDENCE_DIR/logcat.txt"

if ! grep -q "Status: ok" "$EVIDENCE_DIR/launch.txt"; then
  echo "Android did not report a successful QA launch. See $EVIDENCE_DIR/launch.txt"
  cat "$EVIDENCE_DIR/launch.txt"
  exit 1
fi

if ! mac_adb shell pidof "$QA_PACKAGE" > "$EVIDENCE_DIR/pid.txt"; then
  echo "QA app did not remain alive after launch. See $EVIDENCE_DIR/logcat.txt"
  exit 1
fi

if grep -A 4 "FATAL EXCEPTION" "$EVIDENCE_DIR/logcat.txt" | grep -q "Process: $QA_PACKAGE" ||
  grep -q "ANR in $QA_PACKAGE" "$EVIDENCE_DIR/logcat.txt"; then
  echo "QA app crashed or stopped responding after launch. See $EVIDENCE_DIR/logcat.txt"
  exit 1
fi

echo "DailyBeat QA passed on-device tests and is running on $MAC_ADB_SERIAL."
echo "Evidence: $EVIDENCE_DIR"
