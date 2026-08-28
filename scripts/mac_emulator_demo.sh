#!/usr/bin/env bash
# Run this on your MacBook (not in Cursor cloud) with Android emulator already running.
# Usage: ./scripts/mac_emulator_demo.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"

echo "=== ADB devices (expect emulator-5554 device) ==="
adb devices -l
if ! adb devices | grep -q 'emulator.*device'; then
  echo "No online emulator. Start one in Android Studio → Device Manager first."
  exit 1
fi

echo "=== Build & install debug APK ==="
./gradlew installDebug

PKG=com.dailybeat.app
echo "=== Grant runtime permissions ==="
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "=== Launch DailyBeat ==="
adb shell am start -n "$PKG/.MainActivity"

echo ""
echo "=== Manual demo on emulator (you will see this on your Mac screen) ==="
echo "1. Today tab → type a manual event → Save event"
echo "2. Tap the mic FAB → emulator uses demo voice transcript → event appears"
echo "3. Generate today's dairy → edit text → Share PDF"
echo "4. Settings tab → toggles, import model from Downloads"
echo ""
echo "Optional: watch mirrored screen with scrcpy"
echo "  brew install scrcpy && scrcpy"
