#!/usr/bin/env bash
# One-time MacBook setup: clone DailyBeat from GitHub and verify build tools.
set -euo pipefail

BRANCH="${DAILYBEAT_BRANCH:-cursor/android-skeleton-cc46}"
TARGET="${DAILYBEAT_DIR:-$HOME/github/dailybeat}"
REPO_URL="https://github.com/sampathmannam/dailybeat.git"

echo "=== DailyBeat Mac setup ==="
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
command -v java >/dev/null || { echo "Install Java 17: brew install openjdk@17"; exit 1; }
command -v adb >/dev/null || {
  echo "adb not found. Add Android SDK platform-tools to PATH, e.g.:"
  echo '  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"'
  exit 1
}

echo "=== Gradle wrapper smoke test ==="
cd android
./gradlew --version

echo ""
echo "Setup OK. Next:"
echo "  1. Start emulator in Android Studio"
echo "  2. ./scripts/mac_sync_and_run.sh"
