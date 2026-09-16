#!/usr/bin/env bash
# Verify and install a pre-built release APK (no Gradle build). Requires Android build-tools/JDK.
#
# Usage:
#   ./scripts/mac_install_release_apk.sh              # auto-pick (phone if both)
#   ./scripts/mac_install_release_apk.sh emulator-5554
#   ./scripts/mac_install_release_apk.sh ZD2232FCR5
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

DEVICE_ARG="${1:-}"
release_version="$(tr -d '[:space:]' < "$ROOT/release/version.txt")"
TAG="${DAILYBEAT_RELEASE_TAG:-v${release_version}}"
if [[ ! "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Expected a stable release tag such as v4.0.6. No download or installation performed."
  exit 1
fi
APK_NAME="DailyBeat-${TAG}.apk"
URL="https://github.com/sampathmannam/dailybeat/releases/download/${TAG}/${APK_NAME}"
PKG=com.dailybeat.app
expected_certificate="44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f"

find_build_tool() {
  local tool_name="$1" candidate tool_path=""
  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return 0
  fi
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  for candidate in "$sdk_root"/build-tools/*/"$tool_name"; do
    if [ -x "$candidate" ]; then tool_path="$candidate"; fi
  done
  if [ -n "$tool_path" ]; then printf '%s\n' "$tool_path"; return 0; fi
  echo "Missing Android build-tool: $tool_name. Install Android SDK build-tools first." >&2
  return 1
}

apksigner="$(find_build_tool apksigner)"
aapt="$(find_build_tool aapt)"
mac_ensure_java

if ! command -v adb >/dev/null; then
  echo "adb not found. See scripts/mac_setup.sh"
  exit 1
fi

adb start-server
adb devices -l
mac_adb_pick_device "$DEVICE_ARG"

download_dir="$(mktemp -d "${TMPDIR:-/tmp}/dailybeat-release.XXXXXX")"
download_apk="$download_dir/$APK_NAME"
checksum_file="$download_dir/SHA256SUMS.txt"
echo "Downloading and verifying $TAG in $download_dir"
curl --proto '=https' --proto-redir '=https' -fsSL -o "$download_apk" "$URL"
curl --proto '=https' --proto-redir '=https' -fsSL -o "$checksum_file" \
  "https://github.com/sampathmannam/dailybeat/releases/download/${TAG}/SHA256SUMS.txt"

# Select only the requested asset. Never execute a downloaded checksum manifest or follow paths
# from it; duplicate/invalid entries fail closed. Legacy unverified assets have no silent fallback.
expected_checksum="$(awk -v asset="$APK_NAME" '$2 == asset { print $1 }' "$checksum_file")"
if [[ ! "$expected_checksum" =~ ^[0-9a-fA-F]{64}$ ]]; then
  echo "Missing or ambiguous release checksum. Nothing installed."
  exit 1
fi
actual_checksum="$(shasum -a 256 "$download_apk" | awk '{print $1}')"
if [ "$(printf '%s' "$expected_checksum" | tr 'A-F' 'a-f')" != "$actual_checksum" ]; then
  echo "Release checksum mismatch. Nothing installed."
  exit 1
fi
certificate_output="$("$apksigner" verify --print-certs "$download_apk")"
actual_certificate="$(printf '%s\n' "$certificate_output" | awk '/^Signer #[0-9]+ certificate SHA-256 digest:/ {print tolower($NF)}')"
if [ "$actual_certificate" != "$expected_certificate" ]; then
  echo "Permanent signing certificate mismatch. Nothing installed."
  exit 1
fi
apk_metadata="$("$aapt" dump badging "$download_apk")"
package_metadata="$(printf '%s\n' "$apk_metadata" | awk '/^package:/ {print}')"
if [[ "$package_metadata" != *"name='$PKG'"* || "$package_metadata" != *"versionName='${TAG#v}'"* ]]; then
  echo "APK package/version does not match the requested DailyBeat release. Nothing installed."
  exit 1
fi

echo "Installing verified $TAG on $MAC_ADB_SERIAL (existing data retained)."
# No uninstall, downgrade flag, or automatic permission grants. Android/app consent stays in charge.
mac_adb install -r "$download_apk"

mac_adb shell am start -n "$PKG/.MainActivity"
echo "Installed DailyBeat $TAG on $MAC_ADB_SERIAL."
echo "Verified APK saved at: $download_apk"
