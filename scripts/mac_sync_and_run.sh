#!/usr/bin/env bash
# Build and run the isolated PatrolGrid QA application on an emulator or phone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEVICE_ARG="${1:-${PATROLGRID_ADB_SERIAL:-}}"
SYNC_REMOTE="${PATROLGRID_SYNC_REMOTE:-0}"
BASE_PACKAGE="${PATROLGRID_APPLICATION_ID:-com.dailybeat.app.patrolgrid}"
PKG="${PATROLGRID_QA_PACKAGE:-${BASE_PACKAGE}.qa}"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

cd "$ROOT"

echo "=== PatrolGrid QA build & run ==="

if [ ! -d "$ROOT/.git" ]; then
  echo "Not a git repo. Clone first:"
  echo "  git clone https://github.com/sampathmannam/dailybeat.git ~/github/dailybeat"
  exit 1
fi

case "$PKG" in
  *.qa) ;;
  *)
    echo "Refusing to use non-QA package '$PKG' in the local QA runner." >&2
    echo "PATROLGRID_QA_PACKAGE must end in .qa." >&2
    exit 1
    ;;
esac

case "$SYNC_REMOTE" in
  0)
    echo "Using the current checkout; no branch or remote changes will be made."
    ;;
  1)
    if [ -n "$(git status --porcelain)" ]; then
      echo "Refusing to sync a checkout with uncommitted changes." >&2
      echo "Commit/stash them, or run without PATROLGRID_SYNC_REMOTE=1." >&2
      exit 1
    fi
    CURRENT_BRANCH="$(git symbolic-ref --quiet --short HEAD || true)"
    [ -n "$CURRENT_BRANCH" ] || {
      echo "Refusing to sync from a detached HEAD." >&2
      exit 1
    }
    if [ -n "${PATROLGRID_BRANCH:-}" ] && [ "$PATROLGRID_BRANCH" != "$CURRENT_BRANCH" ]; then
      echo "Current branch is '$CURRENT_BRANCH', not requested '$PATROLGRID_BRANCH'." >&2
      echo "Switch branches yourself after confirming the checkout is safe." >&2
      exit 1
    fi
    echo "=== Fast-forward $CURRENT_BRANCH from origin ==="
    git fetch origin "$CURRENT_BRANCH"
    git merge --ff-only "origin/$CURRENT_BRANCH"
    ;;
  *)
    echo "PATROLGRID_SYNC_REMOTE must be 0 or 1." >&2
    exit 1
    ;;
esac

echo "=== ADB — connect to emulator or phone ==="
if ! command -v adb >/dev/null; then
  echo "adb not found. Add Android SDK platform-tools to PATH:"
  echo '  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"'
  exit 1
fi

adb start-server >/dev/null
mac_adb devices -l
mac_adb_pick_device "$DEVICE_ARG"

mac_ensure_java

echo "=== Build & install PatrolGrid QA on $MAC_ADB_SERIAL ==="
cd "$ROOT/android"
./gradlew :app:installDebug

mac_adb shell pm path "$PKG" >/dev/null || {
  echo "Expected QA package '$PKG' was not installed." >&2
  exit 1
}

if [ "${PATROLGRID_GRANT_QA_PERMISSIONS:-0}" = "1" ]; then
  echo "=== Grant QA-only test permissions ==="
  mac_adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
  mac_adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
  mac_adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
else
  echo "Runtime permissions remain ungranted so the real consent flow can be tested."
fi

LAUNCHER_COMPONENT="$(mac_adb shell cmd package resolve-activity --brief "$PKG" \
  | tr -d '\r' | tail -1)"
case "$LAUNCHER_COMPONENT" in
  */*) ;;
  *)
    echo "Could not resolve the PatrolGrid QA launcher activity." >&2
    exit 1
    ;;
esac

echo "=== Launch PatrolGrid QA ==="
mac_adb shell am start -n "$LAUNCHER_COMPONENT"

echo ""
echo "PatrolGrid QA ($PKG) is running on $MAC_ADB_SERIAL."
echo "Mirror: brew install scrcpy && scrcpy -s $MAC_ADB_SERIAL"
