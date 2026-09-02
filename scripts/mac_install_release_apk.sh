#!/usr/bin/env -S -i PATROLGRID_CLEAN_ENV=1 HOME=/Users/sujithsampath PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C /bin/bash --noprofile --norc
# shellcheck shell=bash disable=SC2016
# Reverify and install only a local staff package emitted by the Mac ceremony.
set -euo pipefail
[[ "${PATROLGRID_CLEAN_ENV:-}" == '1' ]] || {
  echo 'PatrolGrid installer requires its clean-environment executable entrypoint.' >&2
  exit 1
}
export PATH='/usr/bin:/bin:/usr/sbin:/sbin'
export LANG='C'
export LC_ALL='C'
unset BASH_ENV ENV CDPATH GLOBIGNORE JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS \
  JAVA_OPTS APKANALYZER_OPTS CLASSPATH GRADLE_OPTS SKIP_JDK_VERSION_CHECK \
  PYTHONHOME PYTHONPATH DYLD_INSERT_LIBRARIES DYLD_LIBRARY_PATH ADB_SERVER_SOCKET \
  ANDROID_ADB_SERVER_ADDRESS ANDROID_ADB_SERVER_PORT ZIPOPT UNZIP UNZIPOPT TAR_OPTIONS \
  SSL_CERT_FILE SSL_CERT_DIR CURL_CA_BUNDLE GIT_SSL_NO_VERIFY http_proxy https_proxy \
  all_proxy no_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly TRUSTED_RELEASE_ROOT='/Library/Application Support/PatrolGrid/release-tools/current'
readonly TRUSTED_LAUNCHER="$TRUSTED_RELEASE_ROOT/bin/patrolgrid-release"
if [[ "${PATROLGRID_TRUSTED_LAUNCHER:-}" != "$TRUSTED_LAUNCHER" ||
    "$ROOT" != "$TRUSTED_RELEASE_ROOT" ]]; then
  echo "PatrolGrid installer must run through the audited root-installed launcher: $TRUSTED_LAUNCHER" >&2
  exit 1
fi
# shellcheck source-path=SCRIPTDIR
# shellcheck source=mac_adb_common.sh
source "$ROOT/scripts/mac_adb_common.sh"

readonly EXPECTED_PACKAGE='com.dailybeat.app.patrolgrid'
readonly EXPECTED_SIGNER='1b1351160170796ec9818047e790a5474c8544ad867f62736e8d93fe2a8c025b'
readonly EXPECTED_REPOSITORY='sampathmannam/dailybeat'
readonly SDK_ROOT='/Users/sujithsampath/Library/Android/sdk'
readonly BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
readonly ADB_FIXED="$SDK_ROOT/platform-tools/adb"
readonly AAPT="$BUILD_TOOLS/aapt"
readonly APKSIGNER="$BUILD_TOOLS/apksigner"
readonly ZIPALIGN="$BUILD_TOOLS/zipalign"
readonly APKANALYZER="$SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer"
readonly APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"
readonly APKANALYZER_JAR="$SDK_ROOT/cmdline-tools/latest/lib/apkanalyzer-classpath.jar"
readonly APKANALYZER_EXECUTOR="$ROOT/scripts/patrolgrid_apkanalyzer.py"
readonly APKANALYZER_CLASSPATH_MANIFEST="$ROOT/release/patrolgrid-apkanalyzer-classpath.sha256"
readonly JAVA_HOME_FIXED='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
readonly CODESIGN='/usr/bin/codesign'
readonly SHASUM='/usr/bin/shasum'
readonly STAT='/usr/bin/stat'
readonly JQ='/usr/bin/jq'
readonly UNZIP='/usr/bin/unzip'
readonly INSTALL='/usr/bin/install'
readonly MANIFEST_VERIFIER="$ROOT/scripts/verify_patrolgrid_release_manifest.py"
readonly MAX_APK_BYTES=536870912
readonly MAX_SBOM_BYTES=268435456
readonly MAX_CHECKSUM_BYTES=1024

TMP_ROOT=''
ADB_SERVER_PID=''

fail() {
  echo "PatrolGrid release install stopped: $*" >&2
  exit 1
}

cleanup() {
  if [[ -n "${MAC_ADB_SERVER_PORT:-}" ]]; then
    "$ADB_FIXED" -P "$MAC_ADB_SERVER_PORT" kill-server >/dev/null 2>&1 || true
  fi
  if [[ -n "${ADB_SERVER_PID:-}" && "$ADB_SERVER_PID" =~ ^[1-9][0-9]*$ ]]; then
    /bin/kill "$ADB_SERVER_PID" >/dev/null 2>&1 || true
    wait "$ADB_SERVER_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TMP_ROOT:-}" && -d "$TMP_ROOT" && ! -L "$TMP_ROOT" &&
      "$TMP_ROOT" == */patrolgrid-install.* ]]; then
    /bin/chmod -R u+rwX -- "$TMP_ROOT" 2>/dev/null || true
    /bin/rm -rf -- "$TMP_ROOT"
  fi
}
trap cleanup EXIT

usage() {
  cat >&2 <<'EOF'
Usage:
  ./scripts/mac_install_release_apk.sh <verified-staff-directory> [adb-serial]

The directory must be the untouched three-file output of the authenticated Mac
release ceremony. This installer has no GitHub, URL, ciphertext, or raw-APK path.
EOF
  exit 2
}

require_fixed_tool() {
  [[ -x "$1" ]] || fail "required fixed tool is missing: $1"
}

require_no_extended_acl() {
  local listing=''
  listing="$(/bin/ls -lde "$1")" || fail "cannot inspect path ACL: $1"
  [[ "$(/usr/bin/printf '%s\n' "$listing" | /usr/bin/wc -l | /usr/bin/tr -d ' ')" == '1' ]] ||
    fail "path has an extended ACL: $1"
}

require_pinned_file() {
  local path="$1"
  local digest="$2"
  local mode=''
  [[ -f "$path" && ! -L "$path" && -s "$path" ]] ||
    fail "pinned install tool is absent, empty, or symlinked: $path"
  [[ "$("$STAT" -f '%Su' "$path")" == 'sujithsampath' ]] ||
    fail "pinned install tool has an unexpected owner: $path"
  mode="$("$STAT" -f '%Lp' "$path")"
  (( (8#$mode & 022) == 0 )) || fail "pinned install tool is group/world-writable: $path"
  require_no_extended_acl "$path"
  [[ "$(sha256_file "$path")" == "$digest" ]] || fail "pinned install tool digest changed: $path"
}

verify_install_toolchain() {
  local codesign_details=''
  require_pinned_file "$ADB_FIXED" \
    '9fdf861259dc807937b13afdd5f053c7fda9f3b7726933fe0e0f45130ecb8dc7'
  require_pinned_file "$AAPT" \
    '170717682f714712c5b6854af73cfe37aeda342ff422384e98d67fc1b490f49b'
  require_pinned_file "$ZIPALIGN" \
    '0427144f4a3fd242c5a159e7088637082539ae556bc1d2bbc2032bb775d47cea'
  require_pinned_file "$BUILD_TOOLS/lib64/libc++.dylib" \
    '834cf92eead41eb0c9368604e5ccf1e17b228ce8169d44583cebfaf779f6d27e'
  require_pinned_file "$BUILD_TOOLS/source.properties" \
    '7dee6632e9ad6cb111da2bb99d747211e27927061b1276d040bb1d71fded5ebb'
  require_pinned_file "$APKSIGNER" \
    'b47549e373b895ce6ca620d0c7887e674d9615ffa837a86ac601dcfd04adb0f0'
  require_pinned_file "$APKSIGNER_JAR" \
    '3716d9311e55d2b0918a2fd9d54ba9e406c5f6abeea700b287f11259bc163dec'
  require_pinned_file "$APKANALYZER" \
    '4574a128bdb0b2787008b0bac0cbf63c52b11fb1c8c751c903f31bbc23956eb6'
  require_pinned_file "$APKANALYZER_JAR" \
    '6569cf37ed9481aac7b3f6f563fd6cfbe46395dd2d59885ee1174dba9bad063a'
  require_pinned_file "$APKANALYZER_CLASSPATH_MANIFEST" \
    'ad5c1983518decc34ce48847f98a3195c12ac10e64a6f323cfc5368ec43fdb14'
  require_pinned_file "$SDK_ROOT/cmdline-tools/latest/source.properties" \
    '215e11e90893196549e86dfd6a024f20848322dc7a3d694bc4847b8e6d849ad1'
  /usr/bin/grep -qx 'Pkg.Revision=20.0' "$SDK_ROOT/cmdline-tools/latest/source.properties" ||
    fail "fixed apkanalyzer is not Android command-line tools 20.0"
  require_pinned_file "$JAVA_HOME_FIXED/bin/java" \
    'd558d095ccb32b6f56bc26faea8061b10fb3d2169da470ad5463d8b1169bf7f3'
  "$CODESIGN" --verify --deep --strict '/Applications/Android Studio.app' >/dev/null 2>&1 ||
    fail "Android Studio/JBR failed Apple code-signing verification"
  codesign_details="$("$CODESIGN" -dv --verbose=4 '/Applications/Android Studio.app' 2>&1)"
  if ! /usr/bin/grep -q '^Identifier=com.google.android.studio$' <<<"$codesign_details" ||
      ! /usr/bin/grep -q '^TeamIdentifier=EQHXZ8M8AV$' <<<"$codesign_details"; then
    fail "Android Studio/JBR is not signed by the pinned Google team"
  fi
  for google_tool in "$ADB_FIXED" "$AAPT" "$ZIPALIGN"; do
    "$CODESIGN" --verify --strict "$google_tool" >/dev/null 2>&1 ||
      fail "Google Android tool failed code-signing verification: $google_tool"
    codesign_details="$("$CODESIGN" -dv --verbose=4 "$google_tool" 2>&1)"
    /usr/bin/grep -q '^TeamIdentifier=EQHXZ8M8AV$' <<<"$codesign_details" ||
      fail "Android tool is not signed by the pinned Google team: $google_tool"
  done
}

sha256_file() {
  "$SHASUM" -a 256 "$1" | /usr/bin/awk '{print tolower($1)}'
}

require_bounded_file() {
  local path="$1"
  local maximum="$2"
  local actual=''
  actual="$("$STAT" -f '%z' "$path")"
  [[ "$actual" =~ ^[1-9][0-9]*$ ]] || fail "release file is empty or has an invalid size"
  (( actual <= maximum )) || fail "release file exceeds its fixed size bound: ${path##*/}"
}

run_apkanalyzer() {
  local stage="$TMP_ROOT/apkanalyzer-$RANDOM-$RANDOM"
  [[ ! -e "$stage" && ! -L "$stage" ]] || fail "private apkanalyzer stage collision"
  "$PYTHON" -I "$APKANALYZER_EXECUTOR" \
    "$SDK_ROOT/cmdline-tools/latest/lib" "$APKANALYZER_JAR" \
    "$APKANALYZER_CLASSPATH_MANIFEST" "$JAVA_HOME_FIXED/bin/java" \
    "$BUILD_TOOLS" "$stage" -- "$@"
}

metadata_value() {
  local apk="$1"
  local name="$2"
  local xml="$3"
  run_apkanalyzer manifest print "$apk" > "$xml"
  /usr/bin/xmllint --xpath \
    "string(//*[local-name()='meta-data' and @*[local-name()='name']='$name']/@*[local-name()='value'])" \
    "$xml" 2>/dev/null
}

signer_digest() {
  local signed_file="$1"
  local output=''
  local digests=''
  output="$("$APKSIGNER" verify --min-sdk-version 26 --verbose --print-certs \
    "$signed_file" 2>&1)" || return 1
  /usr/bin/grep -qF 'Verified using v1 scheme (JAR signing): false' <<<"$output" &&
    /usr/bin/grep -qF 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$output" &&
    /usr/bin/grep -qF 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$output" &&
    /usr/bin/grep -qF 'Verified using v4 scheme (APK Signature Scheme v4): false' <<<"$output" &&
    /usr/bin/grep -qF 'Number of signers: 1' <<<"$output" || return 1
  digests="$(sed -n \
    -e 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' \
    -e 's/^V[0-9][0-9.]* Signer: certificate SHA-256 digest: //p' \
    <<<"$output" | tr '[:upper:]' '[:lower:]' |
    sed 's/[^0-9a-f]//g; /^$/d' | sort -u)"
  [[ "$(sed '/^$/d' <<<"$digests" | wc -l | tr -d ' ')" == '1' ]] || return 1
  printf '%s\n' "$digests"
}

select_release_device() {
  local requested="$1"
  local ready_devices="$2"
  local count=''
  if [[ -n "$requested" ]]; then
    MAC_ADB_SERIAL="$requested"
  else
    count="$(sed '/^$/d' <<<"$ready_devices" | wc -l | tr -d ' ')"
    if [[ "$count" == '0' ]]; then
      echo "No adb device is in the ready 'device' state." >&2
      return 1
    fi
    if (( count > 1 )); then
      echo "Multiple devices connected. Pass the intended device id explicitly:" >&2
      while IFS= read -r serial; do
        [[ -n "$serial" ]] || continue
        echo "  ./scripts/mac_install_release_apk.sh <verified-staff-directory> '$serial'" >&2
      done <<<"$ready_devices"
      return 1
    fi
    MAC_ADB_SERIAL="$(sed '/^$/d' <<<"$ready_devices")"
  fi
  export ANDROID_SERIAL="$MAC_ADB_SERIAL"
  echo "Using device: $MAC_ADB_SERIAL"
}

[[ "$#" -ge 1 && "$#" -le 2 ]] || usage
REQUESTED_DIR="$1"
DEVICE_ARG="${2:-}"
[[ ! -L "$REQUESTED_DIR" && -d "$REQUESTED_DIR" ]] ||
  fail "verified staff directory is missing or symlinked"
RELEASE_DIR="$(cd "$REQUESTED_DIR" && pwd -P)"
[[ "$RELEASE_DIR" != '/' ]] || fail "filesystem root is not a staff package"
[[ "$("$STAT" -f '%Su' "$RELEASE_DIR")" == 'sujithsampath' &&
    "$("$STAT" -f '%Lp' "$RELEASE_DIR")" == '700' ]] ||
  fail "verified staff directory must remain release-account-owned mode 0700"
require_no_extended_acl "$RELEASE_DIR"
if [[ -n "$DEVICE_ARG" && ! "$DEVICE_ARG" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  fail "adb serial contains unsupported characters"
fi

for tool in "$ADB_FIXED" "$AAPT" "$APKSIGNER" "$ZIPALIGN" "$APKANALYZER" \
  "$SHASUM" "$STAT" "$JQ" "$UNZIP" "$INSTALL" "$CODESIGN" "$APKANALYZER_EXECUTOR" \
  "$MANIFEST_VERIFIER" '/usr/bin/xmllint'; do
  require_fixed_tool "$tool"
done
[[ -x "$JAVA_HOME_FIXED/bin/java" ]] || fail "the fixed Android Studio JBR is missing"
export JAVA_HOME="$JAVA_HOME_FIXED"
verify_install_toolchain
export MAC_ADB_BINARY="$ADB_FIXED"

shopt -s nullglob dotglob
SOURCE_ENTRIES=("$RELEASE_DIR"/*)
shopt -u nullglob dotglob
[[ "${#SOURCE_ENTRIES[@]}" == '3' ]] || fail "staff package must contain exactly three files"
VERSION=''
MAJOR=''
MINOR=''
PATCH=''
for entry in "${SOURCE_ENTRIES[@]}"; do
  [[ -f "$entry" && ! -L "$entry" && -s "$entry" ]] ||
    fail "staff package must contain only non-empty regular files"
  [[ "$("$STAT" -f '%Su' "$entry")" == 'sujithsampath' &&
      "$("$STAT" -f '%Lp' "$entry")" == '600' ]] ||
    fail "staff package files must remain release-account-owned mode 0600"
  require_no_extended_acl "$entry"
  if [[ "${entry##*/}" =~ ^PatrolGrid-((0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*))\.apk$ ]]; then
    [[ -z "$VERSION" ]] || fail "staff package contains multiple canonical APKs"
    VERSION="${BASH_REMATCH[1]}"
    MAJOR="${BASH_REMATCH[2]}"
    MINOR="${BASH_REMATCH[3]}"
    PATCH="${BASH_REMATCH[4]}"
  fi
done
[[ -n "$VERSION" ]] || fail "staff package has no canonical PatrolGrid APK"
(( MINOR <= 99 && PATCH <= 99 )) || fail "invalid PatrolGrid semantic version"
EXPECTED_VERSION_CODE=$((10#$MAJOR * 10000 + 10#$MINOR * 100 + 10#$PATCH))
(( EXPECTED_VERSION_CODE >= 1 && EXPECTED_VERSION_CODE <= 2100000000 )) ||
  fail "invalid Android version code"

APK_NAME="PatrolGrid-$VERSION.apk"
CHECKSUM_NAME="PatrolGrid-$VERSION-SHA256SUMS.txt"
SBOM_NAME="PatrolGrid-$VERSION.spdx.json"
METADATA_ASSET_NAME='assets/patrolgrid-release.json'
METADATA_JSON_NAME="PatrolGrid-$VERSION-release.json"
for name in "$APK_NAME" "$CHECKSUM_NAME" "$SBOM_NAME"; do
  [[ -f "$RELEASE_DIR/$name" && ! -L "$RELEASE_DIR/$name" && -s "$RELEASE_DIR/$name" ]] ||
    fail "staff package does not match the exact APK/checksum/SBOM layout"
done
require_bounded_file "$RELEASE_DIR/$APK_NAME" "$MAX_APK_BYTES"
require_bounded_file "$RELEASE_DIR/$CHECKSUM_NAME" "$MAX_CHECKSUM_BYTES"
require_bounded_file "$RELEASE_DIR/$SBOM_NAME" "$MAX_SBOM_BYTES"

umask 077
TMP_ROOT="$(/usr/bin/mktemp -d '/tmp/patrolgrid-install.XXXXXX')"
[[ -d "$TMP_ROOT" && ! -L "$TMP_ROOT" ]] || fail "could not create private install snapshot"
/bin/chmod 0700 "$TMP_ROOT"
/bin/chmod -N "$TMP_ROOT" || fail "could not clear inherited install-snapshot ACL"
[[ "$("$STAT" -f '%Su' "$TMP_ROOT")" == 'sujithsampath' &&
    "$("$STAT" -f '%Lp' "$TMP_ROOT")" == '700' ]] ||
  fail "install snapshot is not release-account-owned mode 0700"
require_no_extended_acl "$TMP_ROOT"
for name in "$APK_NAME" "$CHECKSUM_NAME" "$SBOM_NAME"; do
  "$INSTALL" -m 0600 "$RELEASE_DIR/$name" "$TMP_ROOT/$name"
  [[ -f "$TMP_ROOT/$name" && ! -L "$TMP_ROOT/$name" ]] || fail "snapshot race detected"
  /bin/chmod -N "$TMP_ROOT/$name" || fail "could not clear inherited install-snapshot file ACL"
  [[ "$("$STAT" -f '%Su' "$TMP_ROOT/$name")" == 'sujithsampath' &&
      "$("$STAT" -f '%Lp' "$TMP_ROOT/$name")" == '600' ]] ||
    fail "install snapshot file is not release-account-owned mode 0600"
  require_no_extended_acl "$TMP_ROOT/$name"
done
require_bounded_file "$TMP_ROOT/$APK_NAME" "$MAX_APK_BYTES"
require_bounded_file "$TMP_ROOT/$CHECKSUM_NAME" "$MAX_CHECKSUM_BYTES"
require_bounded_file "$TMP_ROOT/$SBOM_NAME" "$MAX_SBOM_BYTES"

/usr/bin/awk -v apk="$APK_NAME" -v sbom="$SBOM_NAME" '
  NF != 2 { exit 1 }
  {
    digest = tolower($1); file = $2; sub(/^\*/, "", file)
    if (digest !~ /^[0-9a-f]{64}$/) exit 1
    if (NR == 1 && file != apk) exit 1
    if (NR == 2 && file != sbom) exit 1
    if (NR > 2) exit 1
  }
  END { if (NR != 2) exit 1 }
' "$TMP_ROOT/$CHECKSUM_NAME" || fail "checksum file does not have the exact APK/SBOM allowlist"
(cd "$TMP_ROOT" && "$SHASUM" -a 256 -c "$CHECKSUM_NAME" >/dev/null) ||
  fail "staff package checksum verification failed"

"$ZIPALIGN" -c -P 16 4 "$TMP_ROOT/$APK_NAME" >/dev/null || fail "APK is not zipaligned"
[[ "$(signer_digest "$TMP_ROOT/$APK_NAME")" == "$EXPECTED_SIGNER" ]] ||
  fail "APK is not signed by the pinned PatrolGrid certificate"
[[ "$("$UNZIP" -Z1 "$TMP_ROOT/$APK_NAME" | /usr/bin/grep -cx "$METADATA_ASSET_NAME")" == '1' ]] ||
  fail "APK-signed release metadata is absent or duplicated"
(umask 077; set -o noclobber; "$UNZIP" -p "$TMP_ROOT/$APK_NAME" \
  "$METADATA_ASSET_NAME" > "$TMP_ROOT/$METADATA_JSON_NAME") ||
  fail "could not extract APK-signed release metadata"
COMMIT="$("$JQ" -r '.commit' "$TMP_ROOT/$METADATA_JSON_NAME")"
TAG="patrolgrid-v$VERSION"
"$JQ" -e --arg package "$EXPECTED_PACKAGE" --arg repo "$EXPECTED_REPOSITORY" \
  --arg version "$VERSION" --argjson code "$EXPECTED_VERSION_CODE" \
  --arg tag "$TAG" \
  'keys == ["applicationId","artifacts","backend","candidate","commit","privacyPolicy","releaseTag","repository","schemaVersion","versionCode","versionName","workflow"] and
   .schemaVersion == 1 and .applicationId == $package and .repository == $repo and
   .versionName == $version and .versionCode == $code and .releaseTag == $tag and
   (.commit|test("^[0-9a-f]{40}$")) and
   (.backend|keys) == ["identity","supabaseAnonKeySha256","supabaseUrlSha256"] and
   .backend.identity != "UNCONFIGURED" and (.backend.identity|test("^https://[^/?#]+/?$")) and
   (.backend.supabaseAnonKeySha256|test("^[0-9a-f]{64}$")) and
   (.backend.supabaseUrlSha256|test("^[0-9a-f]{64}$")) and
   (.privacyPolicy|keys) == ["noticeVersion","sha256","status","url"] and
   .privacyPolicy.noticeVersion == 3 and .privacyPolicy.status == "APPROVED" and
   .privacyPolicy.url == ("https://github.com/"+$repo+"/blob/"+.commit+"/docs/PATROLGRID_PRIVACY_POLICY.md") and
   (.privacyPolicy.sha256|test("^[0-9a-f]{64}$")) and
   (.workflow|keys) == ["ref","runAttempt","runId"] and
   .workflow.ref == ($repo+"/.github/workflows/release.yml@refs/tags/"+$tag) and
   .workflow.runId > 0 and .workflow.runAttempt > 0 and
   (.candidate|keys) == ["actionsArtifactArchiveSha256","actionsArtifactId","assetName","ciphertextSha256","draftAssetId","draftReleaseId","manifestSha256","unsignedApkSha256"] and
   (.candidate.assetName|test("^PatrolGrid-[0-9]+\\.[0-9]+\\.[0-9]+-[0-9a-f]{40}-unsigned-candidate\\.tar\\.gpg$")) and
   (.candidate.actionsArtifactId|type) == "number" and .candidate.actionsArtifactId > 0 and
   (.candidate.draftAssetId|type) == "number" and .candidate.draftAssetId > 0 and
   (.candidate.draftReleaseId|type) == "number" and .candidate.draftReleaseId > 0 and
   all(.candidate|to_entries[]|select(.key|endswith("Sha256"));
     (.value|type) == "string" and (.value|test("^[0-9a-f]{64}$"))) and
   (.artifacts|keys) == ["androidManifestSha256","mappingSha256","sbomSha256"] and
   all(.artifacts[]; test("^[0-9a-f]{64}$"))' \
  "$TMP_ROOT/$METADATA_JSON_NAME" >/dev/null || fail "signed release metadata schema is invalid"
[[ "$("$JQ" -r '.candidate.assetName' "$TMP_ROOT/$METADATA_JSON_NAME")" == \
    "PatrolGrid-$VERSION-$COMMIT-unsigned-candidate.tar.gpg" ]] ||
  fail "signed metadata filename/commit binding is invalid"
[[ "$("$JQ" -r '.artifacts.sbomSha256' "$TMP_ROOT/$METADATA_JSON_NAME")" == \
    "$(sha256_file "$TMP_ROOT/$SBOM_NAME")" ]] || fail "SBOM hash does not match metadata"
"$JQ" -e '.spdxVersion == "SPDX-2.3" and (.packages|type) == "array"' \
  "$TMP_ROOT/$SBOM_NAME" >/dev/null || fail "SBOM is not valid SPDX JSON"

PACKAGE_LINE="$("$AAPT" dump badging "$TMP_ROOT/$APK_NAME" | sed -n '1p')"
ACTUAL_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$PACKAGE_LINE")"
ACTUAL_VERSION="$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$PACKAGE_LINE")"
ACTUAL_CODE="$(sed -n "s/.* versionCode='\([^']*\)'.*/\1/p" <<<"$PACKAGE_LINE")"
[[ "$ACTUAL_PACKAGE" == "$EXPECTED_PACKAGE" && "$ACTUAL_VERSION" == "$VERSION" &&
    "$ACTUAL_CODE" == "$EXPECTED_VERSION_CODE" ]] || fail "APK package/version identity mismatch"
[[ "$(run_apkanalyzer manifest debuggable "$TMP_ROOT/$APK_NAME" | tr -d '\r\n')" == 'false' ]] ||
  fail "release APK is debuggable"
APK_COMMIT="$(metadata_value "$TMP_ROOT/$APK_NAME" \
  'com.dailybeat.app.patrolgrid.RELEASE_COMMIT' "$TMP_ROOT/AndroidManifest.xml")"
APK_BACKEND="$(metadata_value "$TMP_ROOT/$APK_NAME" \
  'com.dailybeat.app.patrolgrid.BACKEND_IDENTITY' "$TMP_ROOT/AndroidManifest.xml")"
APK_PRIVACY_STATUS="$(metadata_value "$TMP_ROOT/$APK_NAME" \
  'com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS' "$TMP_ROOT/AndroidManifest.xml")"
APK_PRIVACY_NOTICE_VERSION="$(metadata_value "$TMP_ROOT/$APK_NAME" \
  'com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION' "$TMP_ROOT/AndroidManifest.xml")"
[[ "$APK_COMMIT" == "$COMMIT" ]] || fail "APK full commit does not match signed metadata"
[[ "$APK_BACKEND" == "$("$JQ" -r '.backend.identity' "$TMP_ROOT/$METADATA_JSON_NAME")" ]] ||
  fail "APK backend identity does not match signed metadata"
[[ "$APK_PRIVACY_STATUS" == \
    "$("$JQ" -r '.privacyPolicy.status' "$TMP_ROOT/$METADATA_JSON_NAME")" &&
    "$APK_PRIVACY_NOTICE_VERSION" == \
    "$("$JQ" -r '.privacyPolicy.noticeVersion' "$TMP_ROOT/$METADATA_JSON_NAME")" ]] ||
  fail "APK privacy approval/version does not match signed metadata"
"$AAPT" dump xmltree "$TMP_ROOT/$APK_NAME" res/xml/network_security_config.xml \
  > "$TMP_ROOT/network-security-config.txt" || fail "APK network-security policy cannot be inspected"
"$AAPT" dump xmltree "$TMP_ROOT/$APK_NAME" res/xml/data_extraction_rules.xml \
  > "$TMP_ROOT/data-extraction-rules.txt" || fail "APK data-extraction policy cannot be inspected"
"$AAPT" dump xmltree "$TMP_ROOT/$APK_NAME" res/xml/file_paths.xml \
  > "$TMP_ROOT/file-paths.txt" || fail "APK FileProvider paths policy cannot be inspected"
"$AAPT" dump --values resources "$TMP_ROOT/$APK_NAME" \
  > "$TMP_ROOT/resources.txt" || fail "APK compiled resource table cannot be inspected"
APK_MANIFEST_SHA256="$(/usr/bin/python3 -I "$MANIFEST_VERIFIER" \
  "$TMP_ROOT/AndroidManifest.xml" "$TMP_ROOT/resources.txt" \
  "$TMP_ROOT/network-security-config.txt" "$TMP_ROOT/data-extraction-rules.txt" \
  "$TMP_ROOT/file-paths.txt" "$EXPECTED_PACKAGE" "$VERSION" \
  "$EXPECTED_VERSION_CODE" "$COMMIT" "$APK_BACKEND" "$APK_PRIVACY_STATUS" \
  "$APK_PRIVACY_NOTICE_VERSION")" || fail "APK merged manifest violates release policy"
[[ "$APK_MANIFEST_SHA256" == \
    "$("$JQ" -r '.artifacts.androidManifestSha256' "$TMP_ROOT/$METADATA_JSON_NAME")" ]] ||
  fail "APK merged manifest hash does not match APK-signed metadata"
echo "Verified PatrolGrid $VERSION ($EXPECTED_VERSION_CODE), commit $COMMIT, non-debuggable, exact signer."

"$ADB_FIXED" kill-server >/dev/null 2>&1 || true
MAC_ADB_SERVER_PORT="$(/usr/bin/python3 -I -c '
import socket
with socket.socket() as listener:
    listener.bind(("127.0.0.1", 0))
    print(listener.getsockname()[1])
')"
[[ "$MAC_ADB_SERVER_PORT" =~ ^[1-9][0-9]*$ ]] || fail "could not select a private adb server port"
export MAC_ADB_SERVER_PORT
"$ADB_FIXED" -P "$MAC_ADB_SERVER_PORT" nodaemon server \
  > "$TMP_ROOT/adb-server.log" 2>&1 &
ADB_SERVER_PID=$!
[[ "$ADB_SERVER_PID" =~ ^[1-9][0-9]*$ ]] || fail "could not launch the pinned adb server"
for _ in {1..30}; do
  /bin/kill -0 "$ADB_SERVER_PID" >/dev/null 2>&1 || fail "pinned adb server exited"
  if "$ADB_FIXED" -P "$MAC_ADB_SERVER_PORT" devices >/dev/null 2>&1; then break; fi
  /bin/sleep 0.1
done
"$ADB_FIXED" -P "$MAC_ADB_SERVER_PORT" devices >/dev/null 2>&1 ||
  fail "pinned adb server did not become ready"
"$ADB_FIXED" -P "$MAC_ADB_SERVER_PORT" devices -l
READY_DEVICES="$(mac_adb_list_devices)"
select_release_device "$DEVICE_ARG" "$READY_DEVICES" || fail "could not select one ready adb target"
READY_MATCHES="$(/usr/bin/awk -v serial="$MAC_ADB_SERIAL" '$0 == serial {count++} END {print count+0}' \
  <<<"$READY_DEVICES")"
[[ "$READY_MATCHES" == '1' ]] || fail "selected adb target is not uniquely ready"
[[ "$(mac_adb get-state 2>/dev/null | tr -d '\r')" == 'device' ]] || fail "selected adb target is not ready"
DEVICE_MODEL="$(mac_adb shell getprop ro.product.model | /usr/bin/tr -d '\r\n')"
DEVICE_FINGERPRINT="$(mac_adb shell getprop ro.build.fingerprint | /usr/bin/tr -d '\r\n')"
[[ -n "$DEVICE_MODEL" && "$DEVICE_MODEL" != *[[:cntrl:]]* &&
    -n "$DEVICE_FINGERPRINT" && "$DEVICE_FINGERPRINT" != *[[:cntrl:]]* ]] ||
  fail "selected device identity could not be bound"
echo "Bound device model: $DEVICE_MODEL"
echo "Bound device build fingerprint: $DEVICE_FINGERPRINT"
mac_adb install -r "$TMP_ROOT/$APK_NAME" || fail "adb could not install verified PatrolGrid"
[[ "$(mac_adb get-state 2>/dev/null | /usr/bin/tr -d '\r')" == 'device' &&
    "$(mac_adb shell getprop ro.product.model | /usr/bin/tr -d '\r\n')" == "$DEVICE_MODEL" &&
    "$(mac_adb shell getprop ro.build.fingerprint | /usr/bin/tr -d '\r\n')" == \
      "$DEVICE_FINGERPRINT" ]] || fail "selected device identity changed after install"
INSTALLED_PATHS="$(mac_adb shell pm path "$EXPECTED_PACKAGE" | /usr/bin/tr -d '\r')" ||
  fail "PatrolGrid is absent after install"
[[ "$(/usr/bin/sed '/^$/d' <<<"$INSTALLED_PATHS" | /usr/bin/wc -l | /usr/bin/tr -d ' ')" == '1' ]]
INSTALLED_REMOTE_APK="$(/usr/bin/sed -n 's/^package:\(\/.*\/base\.apk\)$/\1/p' \
  <<<"$INSTALLED_PATHS")"
[[ -n "$INSTALLED_REMOTE_APK" && "$INSTALLED_REMOTE_APK" != *$'\n'* ]] ||
  fail "installed package does not expose exactly one canonical base.apk"
mac_adb pull "$INSTALLED_REMOTE_APK" "$TMP_ROOT/installed-base.apk" >/dev/null ||
  fail "could not retrieve the installed base.apk for independent verification"
[[ -f "$TMP_ROOT/installed-base.apk" && ! -L "$TMP_ROOT/installed-base.apk" &&
    -s "$TMP_ROOT/installed-base.apk" ]] || fail "retrieved installed APK is unsafe"
[[ "$(sha256_file "$TMP_ROOT/installed-base.apk")" == \
    "$(sha256_file "$TMP_ROOT/$APK_NAME")" ]] ||
  fail "installed base.apk is not byte-for-byte the verified release APK"
"$ZIPALIGN" -c -P 16 4 "$TMP_ROOT/installed-base.apk" >/dev/null ||
  fail "installed base.apk lost required alignment"
[[ "$(signer_digest "$TMP_ROOT/installed-base.apk")" == "$EXPECTED_SIGNER" ]] ||
  fail "installed base.apk signer does not match the pinned PatrolGrid certificate"
INSTALLED_PACKAGE_LINE="$("$AAPT" dump badging "$TMP_ROOT/installed-base.apk" | /usr/bin/sed -n '1p')"
[[ "$INSTALLED_PACKAGE_LINE" == "package: name='$EXPECTED_PACKAGE'"* &&
    "$INSTALLED_PACKAGE_LINE" == *" versionCode='$EXPECTED_VERSION_CODE'"* &&
    "$INSTALLED_PACKAGE_LINE" == *" versionName='$VERSION'"* ]] ||
  fail "installed base.apk package/version identity mismatch"
[[ "$(run_apkanalyzer manifest debuggable "$TMP_ROOT/installed-base.apk" | /usr/bin/tr -d '\r\n')" == 'false' ]] ||
  fail "installed base.apk is debuggable"
[[ "$(metadata_value "$TMP_ROOT/installed-base.apk" \
      'com.dailybeat.app.patrolgrid.RELEASE_COMMIT' "$TMP_ROOT/installed-AndroidManifest.xml")" == \
    "$COMMIT" ]] || fail "installed base.apk commit metadata mismatch"
[[ "$(metadata_value "$TMP_ROOT/installed-base.apk" \
      'com.dailybeat.app.patrolgrid.BACKEND_IDENTITY' "$TMP_ROOT/installed-AndroidManifest.xml")" == \
    "$APK_BACKEND" ]] || fail "installed base.apk backend metadata mismatch"
[[ "$(metadata_value "$TMP_ROOT/installed-base.apk" \
      'com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS' "$TMP_ROOT/installed-AndroidManifest.xml")" == \
    "$APK_PRIVACY_STATUS" &&
    "$(metadata_value "$TMP_ROOT/installed-base.apk" \
      'com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION' "$TMP_ROOT/installed-AndroidManifest.xml")" == \
    "$APK_PRIVACY_NOTICE_VERSION" ]] || fail "installed base.apk privacy metadata mismatch"
run_apkanalyzer manifest print "$TMP_ROOT/installed-base.apk" \
  > "$TMP_ROOT/installed-AndroidManifest.xml"
"$AAPT" dump xmltree "$TMP_ROOT/installed-base.apk" res/xml/network_security_config.xml \
  > "$TMP_ROOT/installed-network-security-config.txt" ||
  fail "installed base.apk network-security policy cannot be inspected"
"$AAPT" dump xmltree "$TMP_ROOT/installed-base.apk" res/xml/data_extraction_rules.xml \
  > "$TMP_ROOT/installed-data-extraction-rules.txt" ||
  fail "installed base.apk data-extraction policy cannot be inspected"
"$AAPT" dump xmltree "$TMP_ROOT/installed-base.apk" res/xml/file_paths.xml \
  > "$TMP_ROOT/installed-file-paths.txt" ||
  fail "installed base.apk FileProvider paths policy cannot be inspected"
"$AAPT" dump --values resources "$TMP_ROOT/installed-base.apk" \
  > "$TMP_ROOT/installed-resources.txt" ||
  fail "installed base.apk compiled resource table cannot be inspected"
[[ "$(/usr/bin/python3 -I "$MANIFEST_VERIFIER" \
      "$TMP_ROOT/installed-AndroidManifest.xml" "$TMP_ROOT/installed-resources.txt" \
      "$TMP_ROOT/installed-network-security-config.txt" \
      "$TMP_ROOT/installed-data-extraction-rules.txt" "$TMP_ROOT/installed-file-paths.txt" \
      "$EXPECTED_PACKAGE" "$VERSION" \
      "$EXPECTED_VERSION_CODE" "$COMMIT" "$APK_BACKEND" "$APK_PRIVACY_STATUS" \
      "$APK_PRIVACY_NOTICE_VERSION")" == "$APK_MANIFEST_SHA256" ]] ||
  fail "installed base.apk merged manifest policy/hash mismatch"
LAUNCHER="$(mac_adb shell cmd package resolve-activity --brief "$EXPECTED_PACKAGE" |
  tr -d '\r' | tail -1)"
case "$LAUNCHER" in
  "$EXPECTED_PACKAGE/"*) mac_adb shell am start -n "$LAUNCHER" ;;
  *) fail "could not resolve the pinned PatrolGrid launcher" ;;
esac
echo "Installed PatrolGrid $VERSION from commit $COMMIT on $MAC_ADB_SERIAL."
echo "Runtime permissions were not auto-granted; the QA package remains separate."
