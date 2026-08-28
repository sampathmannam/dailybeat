#!/usr/bin/env bash
# Run on your MacBook with emulator or phone connected via adb.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -f "$ROOT/scripts/mac_sync_and_run.sh" ]; then
  exec "$ROOT/scripts/mac_sync_and_run.sh"
else
  cd "$ROOT/android"
  ./gradlew installDebug
  adb shell am start -n com.dailybeat.app/.MainActivity
fi
