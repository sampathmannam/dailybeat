#!/usr/bin/env bash
# Install pre-built release APK from GitHub (no Gradle build). Mac only.
set -euo pipefail

TAG="${DAILYBEAT_RELEASE_TAG:-v2.0.0}"
APK_NAME="app-release.apk"
TMP="${TMPDIR:-/tmp}/dailybeat-${TAG}.apk"
URL="https://github.com/sampathmannam/dailybeat/releases/download/${TAG}/${APK_NAME}"
PKG=com.dailybeat.app

if ! command -v adb >/dev/null; then
  echo "adb not found. See scripts/mac_setup.sh"
  exit 1
fi

adb start-server
adb devices -l
if ! adb devices | grep -E 'emulator|device$' | grep -v 'List of' | grep -q 'device'; then
  echo "Connect an emulator or phone first (USB debugging on)."
  exit 1
fi

echo "=== Download $TAG release APK ==="
curl -fsSL -o "$TMP" "$URL" || {
  echo "Download failed. Open: https://github.com/sampathmannam/dailybeat/releases/tag/$TAG"
  exit 1
}

echo "=== Install ==="
adb install -r "$TMP"

adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

adb shell am start -n "$PKG/.MainActivity"
echo "Installed DailyBeat $TAG from GitHub Releases."
