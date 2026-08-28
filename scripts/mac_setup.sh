#!/usr/bin/env bash
# One-time MacBook setup: clone DailyBeat from GitHub and verify build tools.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

BRANCH="${DAILYBEAT_BRANCH:-main}"
TARGET="${DAILYBEAT_DIR:-$HOME/github/dailybeat}"
REPO_URL="https://github.com/sampathmannam/dailybeat.git"

echo "=== DailyBeat Mac setup (v2.0.0) ==="
echo "Target: $TARGET"
echo "Branch: $BRANCH"

if [ ! -d "$TARGET/.git" ]; then
  mkdir -p "$(dirname "$TARGET")"
  git clone "$REPO_URL" "$TARGET"
fi

cd "$TARGET"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo "=== Checking tools ==="

if ! command -v adb >/dev/null; then
  echo "adb not found. Add Android SDK platform-tools to PATH, e.g.:"
  echo '  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"'
  exit 1
fi
echo "ADB:  $(adb version | head -1)"

if mac_ensure_java; then
  echo "Java: $(java -version 2>&1 | head -1)"
  echo "=== Gradle wrapper smoke test ==="
  cd android
  ./gradlew --version
else
  echo ""
  echo "Setup partial OK (adb works). Install Java before building from source."
fi

echo ""
echo "Next steps:"
echo "  1. Connect device: adb devices"
echo "  2. Install release APK (no Java):"
echo "     cd $TARGET"
echo "     ./scripts/mac_install_release_apk.sh"
echo "  3. Or build locally (needs Java):"
echo "     ./scripts/mac_sync_and_run.sh"
echo ""
echo "Two devices connected? Pick one:"
echo "  export DAILYBEAT_ADB_SERIAL=ZD2232FCR5      # Motorola phone"
echo "  export DAILYBEAT_ADB_SERIAL=emulator-5554 # Emulator"
echo "Or: DAILYBEAT_DEVICE=emulator ./scripts/mac_install_release_apk.sh"
