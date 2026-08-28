#!/usr/bin/env bash
# One-time MacBook setup: clone DailyBeat from GitHub and verify build tools.
set -euo pipefail

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
command -v java >/dev/null || {
  echo "Install Java 17: brew install openjdk@17"
  exit 1
}

if ! command -v adb >/dev/null; then
  echo "adb not found. Add Android SDK platform-tools to PATH, e.g.:"
  echo '  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"'
  echo ""
  echo "Or install Android Studio and open SDK Manager → Android SDK Platform-Tools"
  exit 1
fi

echo "Java: $(java -version 2>&1 | head -1)"
echo "ADB:  $(adb version | head -1)"

echo "=== Gradle wrapper smoke test ==="
cd android
./gradlew --version

echo ""
echo "Setup OK. Next steps on your laptop:"
echo "  1. Android Studio → Device Manager → start an emulator (or plug in phone)"
echo "  2. cd $TARGET && ./scripts/mac_sync_and_run.sh"
echo ""
echo "Or install release APK without building:"
echo "  ./scripts/mac_install_release_apk.sh"
