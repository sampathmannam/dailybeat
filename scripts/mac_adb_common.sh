#!/usr/bin/env bash
# Shared adb + Java helpers for Mac scripts. Source, do not execute directly.
set -euo pipefail

# Set by mac_adb_pick_device — all mac_adb calls use adb -s explicitly.
MAC_ADB_SERIAL=""

mac_ensure_java() {
  if command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
    return 0
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local home
    home="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
    if [ -n "$home" ]; then
      export JAVA_HOME="$home"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  fi

  for candidate in \
    "/opt/homebrew/opt/openjdk@17" \
    "/usr/local/opt/openjdk@17" \
    "/opt/homebrew/opt/openjdk@21" \
    "/usr/local/opt/openjdk@21"; do
    if [ -d "$candidate/bin" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done

  echo "Java not found. Install Java 17 (required for Gradle builds):"
  echo "  brew install openjdk@17"
  echo "  echo 'export PATH=\"/opt/homebrew/opt/openjdk@17/bin:\$PATH\"' >> ~/.zshrc"
  echo "  source ~/.zshrc"
  echo ""
  echo "To install APK without building (no Java needed):"
  echo "  ./scripts/mac_install_release_apk.sh emulator-5554"
  return 1
}

mac_adb_list_devices() {
  adb devices | awk 'NR>1 && $2=="device" {print $1}'
}

mac_adb_pick_device() {
  local explicit="${1:-}"

  if [ -n "$explicit" ]; then
    MAC_ADB_SERIAL="$explicit"
    export ANDROID_SERIAL="$MAC_ADB_SERIAL"
    echo "Using device: $MAC_ADB_SERIAL (argument)"
    return 0
  fi

  if [ -n "${DAILYBEAT_ADB_SERIAL:-}" ]; then
    MAC_ADB_SERIAL="$DAILYBEAT_ADB_SERIAL"
    export ANDROID_SERIAL="$MAC_ADB_SERIAL"
    echo "Using device: $MAC_ADB_SERIAL (DAILYBEAT_ADB_SERIAL)"
    return 0
  fi

  local devices
  devices="$(mac_adb_list_devices)"
  local count
  count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"

  if [ "${count:-0}" -eq 0 ]; then
    echo "No adb device in 'device' state. Start emulator or connect phone with USB debugging."
    return 1
  fi

  if [ "${count:-0}" -eq 1 ]; then
    MAC_ADB_SERIAL="$(printf '%s\n' "$devices" | head -1)"
    export ANDROID_SERIAL="$MAC_ADB_SERIAL"
    echo "Using device: $MAC_ADB_SERIAL"
    return 0
  fi

  local pick=""
  case "${DAILYBEAT_DEVICE:-phone}" in
    emulator)
      pick="$(printf '%s\n' "$devices" | grep '^emulator-' | head -1)"
      ;;
    phone)
      pick="$(printf '%s\n' "$devices" | grep -v '^emulator-' | head -1)"
      ;;
    *)
      pick="$(printf '%s\n' "$devices" | head -1)"
      ;;
  esac

  if [ -z "$pick" ]; then
    echo "Multiple devices connected. Pass device id as argument:"
    printf '%s\n' "$devices" | while read -r id; do
      echo "  ./scripts/mac_install_release_apk.sh $id"
    done
    return 1
  fi

  MAC_ADB_SERIAL="$pick"
  export ANDROID_SERIAL="$MAC_ADB_SERIAL"
  echo "Multiple devices — using $MAC_ADB_SERIAL (DAILYBEAT_DEVICE=${DAILYBEAT_DEVICE:-phone})"
  echo "Other devices:"
  printf '%s\n' "$devices" | grep -v "^${pick}$" || true
}

mac_adb() {
  if [ -n "$MAC_ADB_SERIAL" ]; then
    adb -s "$MAC_ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}
