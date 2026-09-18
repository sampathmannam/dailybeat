#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 DEVICE_SERIAL TRIAL_ID capture_on|capture_off [OUTPUT_DIR]" >&2
  exit 2
fi

device_serial="$1"
trial_id="$2"
phase="$3"
output_dir="${4:-deliverables/battery-field-trial-${trial_id}}"
package_name="${DAILYBEAT_PACKAGE:-com.dailybeat.app}"

[[ "$device_serial" =~ ^[A-Za-z0-9._:-]+$ ]] || { echo "Invalid device serial." >&2; exit 2; }
[[ "$trial_id" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Invalid trial ID." >&2; exit 2; }
[[ "$phase" == "capture_on" || "$phase" == "capture_off" ]] || {
  echo "Phase must be capture_on or capture_off." >&2
  exit 2
}

state="$(adb -s "$device_serial" get-state 2>/dev/null || true)"
[[ "$state" == "device" ]] || { echo "Device $device_serial is not available." >&2; exit 1; }

timestamp_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
safe_timestamp="${timestamp_utc//:/-}"
sample_dir="$output_dir/raw/$device_serial/$safe_timestamp"
mkdir -p "$sample_dir"

battery_dump="$(adb -s "$device_serial" shell dumpsys battery)"
level="$(printf '%s\n' "$battery_dump" | awk -F': ' '/^[[:space:]]*level:/{print $2; exit}')"
status="$(printf '%s\n' "$battery_dump" | awk -F': ' '/^[[:space:]]*status:/{print $2; exit}')"
temperature_tenths_c="$(printf '%s\n' "$battery_dump" | awk -F': ' '/^[[:space:]]*temperature:/{print $2; exit}')"
plugged="$(printf '%s\n' "$battery_dump" | awk -F': ' '/^[[:space:]]*plugged:/{print $2; exit}')"
model="$(adb -s "$device_serial" shell getprop ro.product.model | tr -d '\r')"
build="$(adb -s "$device_serial" shell getprop ro.build.fingerprint | tr -d '\r')"
version="$(adb -s "$device_serial" shell dumpsys package "$package_name" | awk -F= '/versionName=/{print $2; exit}' | tr -d '\r')"

printf '%s\n' "$battery_dump" > "$sample_dir/battery.txt"
adb -s "$device_serial" shell dumpsys batterystats --charged "$package_name" \
  > "$sample_dir/dailybeat-batterystats.txt"
adb -s "$device_serial" shell dumpsys deviceidle > "$sample_dir/device-idle.txt"
adb -s "$device_serial" shell dumpsys package "$package_name" > "$sample_dir/package.txt"

csv="$output_dir/battery-samples.csv"
if [[ ! -f "$csv" ]]; then
  printf '%s\n' 'timestamp_utc,device_serial,model,build_fingerprint,package,version_name,phase,battery_level,status,plugged,temperature_tenths_c' > "$csv"
fi
csv_escape() { printf '"%s"' "${1//\"/\"\"}"; }
{
  csv_escape "$timestamp_utc"; printf ','
  csv_escape "$device_serial"; printf ','
  csv_escape "$model"; printf ','
  csv_escape "$build"; printf ','
  csv_escape "$package_name"; printf ','
  csv_escape "$version"; printf ','
  csv_escape "$phase"; printf ',%s,%s,%s,%s\n' "$level" "$status" "$plugged" "$temperature_tenths_c"
} >> "$csv"

echo "Recorded $phase sample at $timestamp_utc without resetting Android battery statistics."
echo "Output: $sample_dir"
