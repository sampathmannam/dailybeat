#!/usr/bin/env bash
# Pull latest from GitHub, build, install on Mac emulator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRANCH="${DAILYBEAT_BRANCH:-cursor/android-skeleton-cc46}"
cd "$ROOT"

echo "=== Pull $BRANCH from GitHub ==="
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo "=== ADB devices ==="
adb devices -l
if ! adb devices | grep -q 'emulator.*device'; then
  echo "No online emulator. Start one in Android Studio → Device Manager."
  exit 1
fi

echo "=== Install debug APK ==="
cd android
./gradlew installDebug

PKG=com.dailybeat.app
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

adb shell am start -n "$PKG/.MainActivity"
echo "DailyBeat launched on your Mac emulator."
