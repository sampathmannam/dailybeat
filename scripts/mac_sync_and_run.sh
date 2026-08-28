#!/usr/bin/env bash
# Pull latest from GitHub (main / v2.0.0), build, install on Mac emulator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRANCH="${DAILYBEAT_BRANCH:-main}"
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

adb kill-server 2>/dev/null || true
adb start-server
adb devices -l

if ! adb devices | grep -E 'emulator|device$' | grep -v 'List of' | grep -q 'device'; then
  echo ""
  echo "No device connected."
  echo "  • Emulator: Android Studio → Device Manager → Start a virtual device"
  echo "  • Phone: USB cable + enable USB debugging"
  echo "Then run this script again."
  exit 1
fi

echo "=== Build & install debug APK ==="
cd "$ROOT/android"
./gradlew installDebug

PKG=com.dailybeat.app
echo "=== Grant permissions (so onboarding is smooth) ==="
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.READ_CALL_LOG 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "=== Launch DailyBeat ==="
adb shell am start -n "$PKG/.MainActivity"

echo ""
echo "DailyBeat v2 is running on your connected device."
echo "Tabs: Today | Diary | History | Settings"
echo "Mirror screen: brew install scrcpy && scrcpy"
