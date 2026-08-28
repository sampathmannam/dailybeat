#!/usr/bin/env bash
# Install pre-built release APK from GitHub (no Gradle build). Mac only.
#
# Usage:
#   ./scripts/mac_install_release_apk.sh              # auto-pick (phone if both)
#   ./scripts/mac_install_release_apk.sh emulator-5554
#   ./scripts/mac_install_release_apk.sh ZD2232FCR5
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

DEVICE_ARG="${1:-}"
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
mac_adb_pick_device "$DEVICE_ARG"

echo "=== Download $TAG release APK → $TMP ==="
curl -fsSL -o "$TMP" "$URL" || {
  echo "Download failed. Open: https://github.com/sampathmannam/dailybeat/releases/tag/$TAG"
  exit 1
}

echo "=== Install on $MAC_ADB_SERIAL ==="
mac_adb install -r "$TMP"

mac_adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

mac_adb shell am start -n "$PKG/.MainActivity"
echo "Installed DailyBeat $TAG on $MAC_ADB_SERIAL."
echo "APK saved at: $TMP"
