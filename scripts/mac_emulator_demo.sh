#!/usr/bin/env bash
# Build and run PatrolGrid QA on an Android emulator, never an attached phone.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Add Android SDK platform-tools to PATH." >&2
  exit 1
fi

DEVICE_ARG="${1:-}"
if [ -z "$DEVICE_ARG" ]; then
  EMULATORS="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1}')"
  EMULATOR_COUNT="$(printf '%s\n' "$EMULATORS" | sed '/^$/d' | wc -l | tr -d ' ')"
  case "$EMULATOR_COUNT" in
    0)
      echo "No ready Android emulator found. Start one in Android Studio." >&2
      exit 1
      ;;
    1)
      DEVICE_ARG="$(printf '%s\n' "$EMULATORS" | head -1)"
      ;;
    *)
      echo "Multiple emulators are ready. Pass the intended emulator id:" >&2
      printf '%s\n' "$EMULATORS" | sed 's/^/  .\/scripts\/mac_emulator_demo.sh /' >&2
      exit 1
      ;;
  esac
fi

case "$DEVICE_ARG" in
  emulator-*) ;;
  *)
    echo "Refusing non-emulator adb target '$DEVICE_ARG'." >&2
    exit 1
    ;;
esac

exec "$ROOT/scripts/mac_sync_and_run.sh" "$DEVICE_ARG"
