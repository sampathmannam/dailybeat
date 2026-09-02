#!/usr/bin/env bash
# Shared adb + Java helpers for Mac scripts. Source, do not execute directly.
set -euo pipefail

# Set by mac_adb_pick_device — all mac_adb calls use adb -s explicitly.
MAC_ADB_SERIAL=""
# Release installation replaces this with the fixed SDK platform-tools path.
# QA helpers retain their existing PATH-based adb behavior.
MAC_ADB_BINARY="${MAC_ADB_BINARY:-adb}"
MAC_ADB_SERVER_PORT="${MAC_ADB_SERVER_PORT:-}"

mac_adb_client() {
  if [ -n "$MAC_ADB_SERVER_PORT" ]; then
    "$MAC_ADB_BINARY" -P "$MAC_ADB_SERVER_PORT" "$@"
  else
    "$MAC_ADB_BINARY" "$@"
  fi
}

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
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
    "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"; do
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
  echo "To install a verified release without a Gradle build (Java is still needed by apksigner):"
  echo "  ./scripts/mac_install_release_apk.sh emulator-5554"
  return 1
}

mac_adb_list_devices() {
  mac_adb_client devices | awk 'NR>1 && $2=="device" {print $1}'
}

mac_adb_pick_device() {
  local explicit="${1:-}"

  if [ -n "$explicit" ]; then
    MAC_ADB_SERIAL="$explicit"
    export ANDROID_SERIAL="$MAC_ADB_SERIAL"
    echo "Using device: $MAC_ADB_SERIAL (argument)"
    return 0
  fi

  if [ -n "${PATROLGRID_ADB_SERIAL:-}" ]; then
    MAC_ADB_SERIAL="$PATROLGRID_ADB_SERIAL"
    export ANDROID_SERIAL="$MAC_ADB_SERIAL"
    echo "Using device: $MAC_ADB_SERIAL (PATROLGRID_ADB_SERIAL)"
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

  echo "Multiple devices connected. Pass the intended device id explicitly:"
  printf '%s\n' "$devices" | while read -r id; do
    echo "  ./scripts/mac_install_release_apk.sh $id"
  done
  return 1
}

mac_adb() {
  if [ -n "$MAC_ADB_SERIAL" ]; then
    mac_adb_client -s "$MAC_ADB_SERIAL" "$@"
  else
    mac_adb_client "$@"
  fi
}
