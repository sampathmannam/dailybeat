#!/usr/bin/env bash
# Pull latest from GitHub (main / v2.0.0), build, install on Mac emulator or phone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRANCH="${DAILYBEAT_BRANCH:-main}"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

cd "$ROOT"

echo "=== DailyBeat Mac sync & run (branch: $BRANCH) ==="

if [ ! -d "$ROOT/.git" ]; then
  echo "Not a git repo. Clone first:"
  echo "  git clone https://github.com/sampathmannam/dailybeat.git ~/github/dailybeat"
  exit 1
fi

echo "=== Pull from GitHub ==="
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo "=== ADB — connect to emulator or phone ==="
if ! command -v adb >/dev/null; then
  echo "adb not found. Add Android SDK platform-tools to PATH:"
  echo '  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"'
  exit 1
fi

mac_adb kill-server 2>/dev/null || true
mac_adb start-server
mac_adb devices -l
mac_adb_pick_device

mac_ensure_java

echo "=== Build & install debug APK on $ANDROID_SERIAL ==="
cd "$ROOT/android"
./gradlew installDebug

PKG=com.dailybeat.app
echo "=== Grant permissions ==="
mac_adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
mac_adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "=== Launch DailyBeat ==="
mac_adb shell am start -n "$PKG/.MainActivity"

echo ""
echo "DailyBeat v2 is running on $ANDROID_SERIAL."
echo "Tabs: Today | Diary | History | Settings"
echo "Mirror: brew install scrcpy && scrcpy -s $ANDROID_SERIAL"
