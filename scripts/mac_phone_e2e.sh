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

echo "=== Build, unit tests, lint, and install QA on $MAC_ADB_SERIAL ==="
cd "$ROOT/android"
./gradlew assembleDebug testDebugUnitTest lintDebug installDebug --no-daemon --stacktrace

QA_PACKAGE="com.dailybeat.app.qa"
mac_adb shell pm grant "$QA_PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$QA_PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "=== Run Compose end-to-end tests on $MAC_ADB_SERIAL ==="
./gradlew connectedDebugAndroidTest --no-daemon --stacktrace

echo "=== Launch QA app and capture evidence ==="
EVIDENCE_DIR="$ROOT/android/app/build/outputs/phone-evidence/$MAC_ADB_SERIAL"
mkdir -p "$EVIDENCE_DIR"
mac_adb logcat -c
mac_adb shell am force-stop "$QA_PACKAGE"
mac_adb shell getprop ro.build.fingerprint > "$EVIDENCE_DIR/device-build.txt"
mac_adb shell dumpsys package "$QA_PACKAGE" > "$EVIDENCE_DIR/package.txt"
mac_adb shell am start -W -n "$QA_PACKAGE/com.dailybeat.app.MainActivity" \
  > "$EVIDENCE_DIR/launch.txt"
sleep 3
mac_adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png"
mac_adb logcat -d -v threadtime > "$EVIDENCE_DIR/logcat.txt"

if ! grep -q "Status: ok" "$EVIDENCE_DIR/launch.txt"; then
  echo "Android did not report a successful QA launch. See $EVIDENCE_DIR/launch.txt"
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
