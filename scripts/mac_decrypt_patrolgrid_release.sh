#!/usr/bin/env -S -i PATROLGRID_CLEAN_ENV=1 HOME=/Users/sujithsampath PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C /bin/bash --noprofile --norc
# shellcheck shell=bash disable=SC2016
# PatrolGrid's fail-closed Mac release ceremony. GitHub supplies only a public-
# key-encrypted unsigned candidate; the permanent APK key never leaves this Mac.
set -euo pipefail
[[ "${PATROLGRID_CLEAN_ENV:-}" == '1' ]] || {
  echo 'PatrolGrid release ceremony requires its clean-environment executable entrypoint.' >&2
  exit 1
}
export PATH='/usr/bin:/bin:/usr/sbin:/sbin'
export LANG='C'
export LC_ALL='C'
unset BASH_ENV ENV CDPATH GLOBIGNORE GH_TOKEN GITHUB_TOKEN GH_ENTERPRISE_TOKEN GH_HOST \
  GH_CONFIG_DIR GNUPGHOME GPG_AGENT_INFO JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS \
  JAVA_OPTS APKANALYZER_OPTS CLASSPATH GRADLE_OPTS SKIP_JDK_VERSION_CHECK \
  PYTHONHOME PYTHONPATH TAR_OPTIONS ZIPOPT UNZIP UNZIPOPT DYLD_INSERT_LIBRARIES \
  DYLD_LIBRARY_PATH ADB_SERVER_SOCKET ANDROID_ADB_SERVER_ADDRESS ANDROID_ADB_SERVER_PORT \
  SSL_CERT_FILE SSL_CERT_DIR CURL_CA_BUNDLE GIT_SSL_NO_VERIFY http_proxy https_proxy \
  all_proxy no_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY \
  GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES \
  GIT_CONFIG GIT_CONFIG_GLOBAL GIT_CONFIG_SYSTEM GIT_CONFIG_COUNT GIT_EXEC_PATH GIT_SSH_COMMAND \
  GIT_COMMON_DIR GIT_SHALLOW_FILE GIT_REPLACE_REF_BASE
export GIT_NO_REPLACE_OBJECTS='1'

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly ROOT
readonly SOURCE_REPOSITORY_ROOT='/Users/sujithsampath/Documents/Codex/2026-09-01/ac/dailybeat'
readonly REPOSITORY='sampathmannam/dailybeat'
readonly EXPECTED_PACKAGE='com.dailybeat.app.patrolgrid'
readonly PRIMARY_FINGERPRINT='AA2B9126F5750A6690CEA90410B087D428F60413'
readonly ENCRYPTION_FINGERPRINT='84AD08D70EC95222457C16CEFEFD926C2C74FB9E'
readonly ENCRYPTION_KEY_ID='FEFD926C2C74FB9E'
readonly APK_CERT_SHA256='1b1351160170796ec9818047e790a5474c8544ad867f62736e8d93fe2a8c025b'
readonly DECRYPTION_SERVICE='PatrolGrid release decryption'
readonly APK_SIGNING_SERVICE='PatrolGrid APK signing'
readonly KEYCHAIN_ACCOUNT='sampathmannam/dailybeat'
readonly RELEASE_GNUPGHOME='/Users/sujithsampath/Library/Application Support/PatrolGrid/gnupg'
readonly RELEASE_KEYSTORE='/Users/sujithsampath/Library/Application Support/PatrolGrid/signing/patrolgrid-release.p12'
readonly RELEASE_KEY_ALIAS='patrolgrid'
readonly TRUSTED_RELEASE_ROOT='/Library/Application Support/PatrolGrid/release-tools/current'
readonly TRUSTED_LAUNCHER="$TRUSTED_RELEASE_ROOT/bin/patrolgrid-release"
readonly MAX_CIPHERTEXT_BYTES=1073741824
readonly MAX_DECRYPTED_ARCHIVE_BYTES=1073741824
readonly MAX_UNSIGNED_APK_BYTES=536870912
readonly MAX_MAPPING_BYTES=268435456
readonly MAX_SBOM_BYTES=268435456
readonly MAX_MANIFEST_BYTES=1048576
readonly PUBLIC_KEY_FILE="$ROOT/release/patrolgrid-release-public-key.asc"
readonly CERTIFICATE_FILE="$ROOT/release/patrolgrid-release-cert.pem"
readonly MANIFEST_VERIFIER="$ROOT/scripts/verify_patrolgrid_release_manifest.py"
readonly PACKET_VERIFIER="$ROOT/scripts/verify_patrolgrid_openpgp_packets.py"
readonly SDK_ROOT='/Users/sujithsampath/Library/Android/sdk'
readonly BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"

readonly SECURITY='/usr/bin/security'
readonly SHASUM='/usr/bin/shasum'
readonly STAT='/usr/bin/stat'
readonly INSTALL='/usr/bin/install'
readonly MKTEMP='/usr/bin/mktemp'
readonly ZIP='/usr/bin/zip'
readonly UNZIP='/usr/bin/unzip'
readonly JQ='/usr/bin/jq'
readonly PYTHON='/usr/bin/python3'
readonly OPENSSL='/usr/bin/openssl'
readonly AAPT="$BUILD_TOOLS/aapt"
readonly APKSIGNER="$BUILD_TOOLS/apksigner"
readonly ZIPALIGN="$BUILD_TOOLS/zipalign"
readonly APKANALYZER="$SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer"
readonly APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"
readonly APKANALYZER_JAR="$SDK_ROOT/cmdline-tools/latest/lib/apkanalyzer-classpath.jar"
readonly APKANALYZER_EXECUTOR="$ROOT/scripts/patrolgrid_apkanalyzer.py"
readonly APKANALYZER_CLASSPATH_MANIFEST="$ROOT/release/patrolgrid-apkanalyzer-classpath.sha256"
readonly OUTPUT_PUBLISHER="$ROOT/scripts/patrolgrid_publish_release.py"
readonly GIT='/usr/bin/git'
readonly DATE='/bin/date'
readonly CAT='/bin/cat'
readonly CODESIGN='/usr/bin/codesign'
readonly FILEVAULT='/usr/bin/fdesetup'
readonly GH='/opt/homebrew/Cellar/gh/2.95.0/bin/gh'
readonly GPG='/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpg'
readonly GPG_AGENT='/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpg-agent'
readonly GPGCONF='/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpgconf'
readonly PRIMARY_KEYGRIP='8EEA02C9C998D20AED561058B3E8C9BE5312E4D8'
readonly ENCRYPTION_KEYGRIP='FEDB72120916F1922A0A62376F0F6F5D765ADC79' # gitleaks:allow -- public OpenPGP keygrip
readonly JAVA_HOME_FIXED='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
readonly KEYTOOL="$JAVA_HOME_FIXED/bin/keytool"

PREPARED_BUNDLE_DIR=''
PREPARED_RELEASE_DIR=''
PREPARED_MAPPING_FILE=''
STAGED_BUNDLE_DIR=''
TEMP_ROOT=''
SECRET_GNUPGHOME=''
RESOLVED_COMMIT=''
RESOLVED_ASSET_NAME=''
RESOLVED_ASSET_DIGEST=''
RESOLVED_RELEASE_BODY=''
RESOLVED_RELEASE_ID=''
RESOLVED_RELEASE_ASSET_ID=''
RESOLVED_WORKFLOW_RUN_ID=''
RESOLVED_ACTIONS_ARTIFACT_ID=''
RESOLVED_ACTIONS_ARTIFACT_DIGEST=''

fail() {
  echo "PatrolGrid release ceremony stopped: $*" >&2
  exit 1
}

git_safe() { GIT_NO_REPLACE_OBJECTS=1 "$GIT" --no-replace-objects "$@"; }

require_trusted_entrypoint() {
  [[ "${PATROLGRID_TRUSTED_LAUNCHER:-}" == "$TRUSTED_LAUNCHER" ]] ||
    fail "run only through the audited root-installed launcher: $TRUSTED_LAUNCHER"
  case "${1:-}" in
    check|create-tag)
      [[ "$ROOT" == "$TRUSTED_RELEASE_ROOT" ]] ||
        fail "$1 must use the root-installed audited helper"
      ;;
    ceremony)
      [[ "$ROOT" != "$SOURCE_REPOSITORY_ROOT" && "$ROOT" == /tmp/.patrolgrid-launcher.*/checkout ]] ||
        fail "ceremony helper must come from the launcher's fresh signed-tag checkout"
      ;;
    *) fail "unsupported trusted entrypoint mode" ;;
  esac
}

cleanup() {
  if [[ -n "${SECRET_GNUPGHOME:-}" && -d "$SECRET_GNUPGHOME" &&
      ! -L "$SECRET_GNUPGHOME" ]]; then
    "$GPGCONF" --homedir "$SECRET_GNUPGHOME" --kill gpg-agent >/dev/null 2>&1 || true
  fi
  if [[ -n "${TEMP_ROOT:-}" && -d "$TEMP_ROOT" && ! -L "$TEMP_ROOT" && \
      "$TEMP_ROOT" == */.patrolgrid-release.* ]]; then
    /bin/chmod -R u+rwX -- "$TEMP_ROOT" 2>/dev/null || true
    /bin/rm -rf -- "$TEMP_ROOT"
  fi
  if [[ -n "${STAGED_BUNDLE_DIR:-}" && -d "$STAGED_BUNDLE_DIR" &&
      ! -L "$STAGED_BUNDLE_DIR" && "$STAGED_BUNDLE_DIR" == */.patrolgrid-bundle.* ]]; then
    /bin/chmod -R u+rwX -- "$STAGED_BUNDLE_DIR" 2>/dev/null || true
    /bin/rm -rf -- "$STAGED_BUNDLE_DIR"
  fi
}
trap cleanup EXIT

usage() {
  cat >&2 <<'EOF'
Usage:
  ./scripts/mac_decrypt_patrolgrid_release.sh check
  ./scripts/mac_decrypt_patrolgrid_release.sh create-tag patrolgrid-vMAJOR.MINOR.PATCH
  ./scripts/mac_decrypt_patrolgrid_release.sh ceremony \
    patrolgrid-vMAJOR.MINOR.PATCH \
    <new-owner-only-release-bundle-directory>

The ceremony resolves the exact authenticated draft itself. It never accepts a
local APK, local ciphertext, repository override, key path, alias, password,
certificate, commit, or workflow-run override.
EOF
  exit 2
}

create_signed_release_tag() {
  local tag="$1"
  local version=''
  local major=''
  local minor=''
  local patch=''
  local version_code=''
  local source_version=''
  local source_code=''
  local main_sha=''
  local head_sha=''
  local signer_name=''
  local signer_email=''
  local tagger=''
  local tag_payload=''
  local tag_signature=''
  local tag_object=''
  local signature_status=''
  local tag_oid=''
  local git_directory=''

  if [[ ! "$tag" =~ ^patrolgrid-v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    fail "tag must be patrolgrid-vMAJOR.MINOR.PATCH"
  fi
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  (( minor <= 99 && patch <= 99 )) || fail "MINOR and PATCH must be at most 99"
  version="$major.$minor.$patch"
  version_code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
  (( version_code >= 1 && version_code <= 2100000000 )) || fail "invalid Android version code"

  git_directory="$(git_safe -C "$SOURCE_REPOSITORY_ROOT" rev-parse --absolute-git-dir)"
  [[ "$git_directory" == "$SOURCE_REPOSITORY_ROOT/.git" && -d "$git_directory" &&
      ! -L "$git_directory" ]] || fail "release source must use its standard non-symlink Git directory"
  [[ -z "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" for-each-ref \
      --format='%(refname)' refs/replace)" ]] || fail "Git replacement refs are forbidden"
  [[ ! -e "$git_directory/info/grafts" && ! -L "$git_directory/info/grafts" ]] ||
    fail "Git grafts are forbidden"
  [[ "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" rev-parse --show-toplevel)" == \
      "$SOURCE_REPOSITORY_ROOT" ]] ||
    fail "release helper is not running from the canonical repository"
  [[ "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" symbolic-ref --short HEAD)" == 'main' ]] ||
    fail "create-tag requires the local main branch"
  [[ -z "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" status --porcelain=v1 --untracked-files=all)" ]] ||
    fail "create-tag requires a completely clean working tree"
  preflight
  tag_payload="$TEMP_ROOT/local-tag-payload"
  tag_signature="$TEMP_ROOT/local-tag-signature.asc"
  tag_object="$TEMP_ROOT/local-tag-object"
  signature_status="$TEMP_ROOT/local-tag-signature-status"
  verify_rulesets
  verify_monotonic_tag_version "$tag" "$version_code" 0
  for trusted_relative in scripts/mac_decrypt_patrolgrid_release.sh \
    scripts/verify_patrolgrid_release_manifest.py \
    scripts/patrolgrid_apkanalyzer.py scripts/patrolgrid_publish_release.py \
    scripts/verify_patrolgrid_openpgp_packets.py \
    release/patrolgrid-apkanalyzer-classpath.sha256 \
    release/patrolgrid-release-public-key.asc release/patrolgrid-release-cert.pem; do
    /usr/bin/cmp -s "$ROOT/$trusted_relative" "$SOURCE_REPOSITORY_ROOT/$trusted_relative" ||
      fail "installed audited release tool/key does not match current protected-main source"
  done
  if git_safe -C "$SOURCE_REPOSITORY_ROOT" show-ref --verify --quiet "refs/tags/$tag"; then
    fail "local release tag already exists: $tag"
  fi
  github_get "/repos/$REPOSITORY/branches/main" "$TEMP_ROOT/tag-main-branch.json"
  "$JQ" -e '.name == "main" and .protected == true' \
    "$TEMP_ROOT/tag-main-branch.json" >/dev/null || fail "main is not protected"
  github_get "/repos/$REPOSITORY/git/ref/heads/main" "$TEMP_ROOT/tag-main-ref.json"
  main_sha="$("$JQ" -r '.object.sha' "$TEMP_ROOT/tag-main-ref.json")"
  head_sha="$(git_safe -C "$SOURCE_REPOSITORY_ROOT" rev-parse HEAD)"
  [[ "$head_sha" =~ ^[0-9a-f]{40}$ && "$head_sha" == "$main_sha" ]] ||
    fail "local main HEAD must exactly equal current protected GitHub main HEAD"

  source_version="$(/usr/bin/sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' \
    "$SOURCE_REPOSITORY_ROOT/android/app/build.gradle.kts")"
  source_code="$(/usr/bin/sed -n 's/^[[:space:]]*versionCode = \([0-9_]*\)/\1/p' \
    "$SOURCE_REPOSITORY_ROOT/android/app/build.gradle.kts" | /usr/bin/tr -d '_')"
  [[ "$source_version" == "$version" && "$source_code" == "$version_code" ]] ||
    fail "tag version/code does not match the reviewed Android source identity"

  signer_name="$(git_safe -C "$SOURCE_REPOSITORY_ROOT" config --get user.name)"
  signer_email="$(git_safe -C "$SOURCE_REPOSITORY_ROOT" config --get user.email)"
  [[ "$signer_name" =~ ^[^\<\>[:cntrl:]]+$ &&
      "$signer_email" =~ ^[^\<\>[:space:][:cntrl:]]+@[^\<\>[:space:][:cntrl:]]+$ ]] ||
    fail "repository Git user.name/user.email are absent or unsafe"
  tagger="$signer_name <$signer_email> $($DATE +%s) $($DATE +%z)"
  /usr/bin/printf 'object %s\ntype commit\ntag %s\ntagger %s\n\nPatrolGrid %s\n' \
    "$head_sha" "$tag" "$tagger" "$version" > "$tag_payload"
  require_regular_private_file "$tag_payload"
  keychain_item_exists "$DECRYPTION_SERVICE"
  "$SECURITY" find-generic-password -w -s "$DECRYPTION_SERVICE" -a "$KEYCHAIN_ACCOUNT" |
    "$GPG" --homedir "$SECRET_GNUPGHOME" --no-options --agent-program "$GPG_AGENT" \
      --batch --yes --no-auto-key-retrieve \
      --pinentry-mode loopback --passphrase-fd 0 --local-user "$PRIMARY_FINGERPRINT!" \
      --armor --detach-sign --output "$tag_signature" "$tag_payload" ||
    fail "could not sign the release tag with the exact PatrolGrid primary key"
  require_regular_private_file "$tag_signature"
  "$GPG" --homedir "$SECRET_GNUPGHOME" --no-options --agent-program "$GPG_AGENT" \
    --batch --no-auto-key-retrieve --status-fd 1 \
    --verify "$tag_signature" "$tag_payload" > "$signature_status" 2>/dev/null ||
    fail "locally generated release tag signature did not verify"
  [[ "$(/usr/bin/awk '$1 == "[GNUPG:]" && $2 == "VALIDSIG" {print $3}' \
    "$signature_status")" == "$PRIMARY_FINGERPRINT" ]] ||
    fail "locally generated tag was not signed by the exact primary key"
  "$CAT" "$tag_payload" "$tag_signature" > "$tag_object"
  tag_oid="$(git_safe -C "$SOURCE_REPOSITORY_ROOT" mktag < "$tag_object")"
  [[ "$tag_oid" =~ ^[0-9a-f]{40}$ ]] || fail "Git rejected the signed annotated tag object"
  git_safe -C "$SOURCE_REPOSITORY_ROOT" update-ref "refs/tags/$tag" "$tag_oid" \
    '0000000000000000000000000000000000000000'
  [[ "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" rev-parse "$tag^{tag}")" == "$tag_oid" &&
      "$(git_safe -C "$SOURCE_REPOSITORY_ROOT" rev-parse "$tag^{commit}")" == "$head_sha" ]] ||
    fail "created tag does not bind the exact protected main commit"
  echo "Created locally verified signed tag $tag at $head_sha."
  echo "Review it, then push only this ref: git push origin refs/tags/$tag"
}

require_executable() {
  [[ -x "$1" ]] || fail "required fixed tool is missing: $1"
}

require_no_extended_acl() {
  local listing=''
  listing="$(/bin/ls -lde "$1")" || fail "cannot inspect path ACL: $1"
  [[ "$(/usr/bin/printf '%s\n' "$listing" | /usr/bin/wc -l | /usr/bin/tr -d ' ')" == '1' ]] ||
    fail "path has an extended ACL: $1"
}

require_designated_private_directory() {
  local path="$1"
  local expected_mode="$2"
  local kind="$3"
  [[ -d "$path" && ! -L "$path" && "$($STAT -f '%Su' "$path")" == 'sujithsampath' &&
      "$($STAT -f '%Lp' "$path")" == "$expected_mode" ]] ||
    fail "$kind is not a release-account-owned mode $expected_mode directory: $path"
  require_no_extended_acl "$path"
}

require_designated_private_file() {
  local path="$1"
  local expected_mode="$2"
  local kind="$3"
  [[ -f "$path" && ! -L "$path" && -s "$path" &&
      "$($STAT -f '%Su' "$path")" == 'sujithsampath' &&
      "$($STAT -f '%Lp' "$path")" == "$expected_mode" ]] ||
    fail "$kind is not a release-account-owned mode $expected_mode file: $path"
  require_no_extended_acl "$path"
}

verified_private_copy() {
  local source="$1"
  local destination="$2"
  local kind="$3"
  "$INSTALL" -m 0600 "$source" "$destination"
  /bin/chmod -N "$destination" || fail "could not clear inherited ACL from $kind"
  require_designated_private_file "$destination" '600' "$kind"
  /usr/bin/cmp -s "$source" "$destination" || fail "$kind differs from its verified source"
}

require_pinned_file() {
  local path="$1"
  local digest="$2"
  local mode=''
  [[ -f "$path" && ! -L "$path" && -s "$path" ]] ||
    fail "pinned release tool is absent, empty, or symlinked: $path"
  [[ "$("$STAT" -f '%Su' "$path")" == 'sujithsampath' ]] ||
    fail "pinned release tool has an unexpected owner: $path"
  mode="$("$STAT" -f '%Lp' "$path")"
  (( (8#$mode & 022) == 0 )) || fail "pinned release tool is group/world-writable: $path"
  require_no_extended_acl "$path"
  [[ "$(sha256_file "$path")" == "$digest" ]] ||
    fail "pinned release tool changed; rollout is blocked pending an independent audit and reinstall: $path"
}

require_pinned_dependency() {
  local loader_path="$1"
  local expected_real_path="$2"
  local digest="$3"
  local actual_real_path=''
  actual_real_path="$("$PYTHON" -I -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' \
    "$loader_path")"
  [[ "$actual_real_path" == "$expected_real_path" ]] ||
    fail "pinned GnuPG dependency resolved to an unexpected path: $loader_path"
  require_pinned_file "$actual_real_path" "$digest"
}

verify_toolchain_integrity() {
  require_pinned_file "$GH" \
    '798882434e7f6ae5846194191263ecc59d56bc201f13f016270f44cb4f34499e'
  require_pinned_file "$GPG" \
    '8fc5f38e275f071a09d0446b6514ceef7de4aee1b64477c382ed6ed1a510502e'
  require_pinned_file "$GPG_AGENT" \
    '350ece1db9830978bd294976f187a4ed043f2d6a42a8642938ceec3529d5e763'
  require_pinned_file "$GPGCONF" \
    'c77d8eb1dc2baa7178d10b2e09e8268028f4846ec211df4c13141edba74bb360'
  require_pinned_dependency '/opt/homebrew/opt/gettext/lib/libintl.8.dylib' \
    '/opt/homebrew/Cellar/gettext/1.0/lib/libintl.8.dylib' \
    '0c6d618e75fea85cc3d631e164a71766fba9341d19ce1f723300c52e63037c51'
  require_pinned_dependency '/opt/homebrew/opt/libgcrypt/lib/libgcrypt.20.dylib' \
    '/opt/homebrew/Cellar/libgcrypt/1.12.2/lib/libgcrypt.20.dylib' \
    '949a342e6afbf8a4fc0dc8ea90841fa52511ca6f33fd0ef77705cf0ca39b7439'
  require_pinned_dependency '/opt/homebrew/opt/readline/lib/libreadline.8.dylib' \
    '/opt/homebrew/Cellar/readline/8.3.3/lib/libreadline.8.3.dylib' \
    '7d74566dcbd3f64a5ec6266c8285e48f0214a9d2f36ad5f158b4282a3f10b9a9'
  require_pinned_dependency '/opt/homebrew/opt/libassuan/lib/libassuan.9.dylib' \
    '/opt/homebrew/Cellar/libassuan/3.0.2/lib/libassuan.9.dylib' \
    '1c45b3dd61f6f07249149723358e4d8448af5ced1a6b279a99ddbd7a906d1ff6'
  require_pinned_dependency '/opt/homebrew/opt/npth/lib/libnpth.0.dylib' \
    '/opt/homebrew/Cellar/npth/1.8/lib/libnpth.0.dylib' \
    'f29d1af471de3e3f2c41f1ac212aeb6e14bb37fabf9551a0ebb93b998c5f4665'
  require_pinned_dependency '/opt/homebrew/opt/libgpg-error/lib/libgpg-error.0.dylib' \
    '/opt/homebrew/Cellar/libgpg-error/1.61/lib/libgpg-error.0.dylib' \
    '8d71d115883e68055c0f81356394bb059eefc0829d13b2dd673cba9641fc452d'
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
  require_pinned_file "$KEYTOOL" \
    'fa53977d8346f06bb63c01c73a19638de7e8f02d08ec5f211824c3d4204d8ca6'
  "$CODESIGN" --verify --deep --strict '/Applications/Android Studio.app' >/dev/null 2>&1 ||
    fail "Android Studio/JBR failed its Apple code-signing verification"
  "$CODESIGN" -dv --verbose=4 '/Applications/Android Studio.app' \
    2> "$TEMP_ROOT/android-studio-codesign.txt"
  if ! /usr/bin/grep -q '^Identifier=com.google.android.studio$' \
      "$TEMP_ROOT/android-studio-codesign.txt" ||
      ! /usr/bin/grep -q '^TeamIdentifier=EQHXZ8M8AV$' \
        "$TEMP_ROOT/android-studio-codesign.txt"; then
    fail "Android Studio/JBR is not signed by the pinned Google team"
  fi
}

initialize_tools() {
  [[ "$(uname -s)" == 'Darwin' ]] || fail "the release ceremony runs only on macOS"
  [[ "${HOME:-}" == '/Users/sujithsampath' && -d "$HOME" && ! -L "$HOME" ]] ||
    fail "release ceremony requires the fixed designated Mac account home"
  for tool in "$SECURITY" "$SHASUM" "$STAT" "$INSTALL" "$MKTEMP" \
    "$ZIP" "$UNZIP" "$JQ" "$PYTHON" "$OPENSSL" "$AAPT" "$APKSIGNER" \
    "$ZIPALIGN" "$APKANALYZER" "$GIT" "$DATE" "$CAT" "$CODESIGN" "$FILEVAULT" \
    "$GPGCONF" "$APKANALYZER_EXECUTOR" "$OUTPUT_PUBLISHER" "$PACKET_VERIFIER" \
    '/usr/bin/xmllint'; do
    require_executable "$tool"
  done
  [[ -x "$JAVA_HOME_FIXED/bin/java" && -x "$KEYTOOL" ]] ||
    fail "the fixed Android Studio JBR is missing"
  [[ -f "$MANIFEST_VERIFIER" && ! -L "$MANIFEST_VERIFIER" && -x "$MANIFEST_VERIFIER" ]] ||
    fail "the signed-tag release manifest verifier is absent or unsafe"
  export JAVA_HOME="$JAVA_HOME_FIXED"
  [[ "$("$FILEVAULT" status 2>/dev/null)" == 'FileVault is On.' ]] ||
    fail "FileVault must be enabled before plaintext release material is handled"
}

require_regular_private_file() {
  local path="$1"
  [[ -f "$path" && ! -L "$path" && -s "$path" ]] || fail "required file is absent or unsafe: $path"
}

run_apkanalyzer() {
  local stage="$TEMP_ROOT/apkanalyzer-$RANDOM-$RANDOM"
  [[ ! -e "$stage" && ! -L "$stage" ]] || fail "private apkanalyzer stage collision"
  "$PYTHON" -I "$APKANALYZER_EXECUTOR" \
    "$SDK_ROOT/cmdline-tools/latest/lib" "$APKANALYZER_JAR" \
    "$APKANALYZER_CLASSPATH_MANIFEST" "$JAVA_HOME_FIXED/bin/java" \
    "$BUILD_TOOLS" "$stage" -- "$@"
}

sha256_file() {
  "$SHASUM" -a 256 "$1" | /usr/bin/awk '{print tolower($1)}'
}

size_file() {
  "$STAT" -f '%z' "$1"
}

keychain_item_exists() {
  "$SECURITY" find-generic-password -s "$1" -a "$KEYCHAIN_ACCOUNT" >/dev/null 2>&1 ||
    fail "required Keychain item is missing: $1 / $KEYCHAIN_ACCOUNT"
}

verify_public_key() {
  local inspection_home="$TEMP_ROOT/public-key-inspection"
  local listing=''
  local fingerprints=''
  require_regular_private_file "$PUBLIC_KEY_FILE"
  "$INSTALL" -d -m 0700 "$inspection_home"
  "$GPG" --homedir "$inspection_home" --no-options --batch --no-auto-key-retrieve \
    --import "$PUBLIC_KEY_FILE" >/dev/null 2>&1
  fingerprints="$("$GPG" --homedir "$inspection_home" --no-options --batch --with-colons \
    --fingerprint --fingerprint --list-keys "$PRIMARY_FINGERPRINT" |
    /usr/bin/awk -F: '$1 == "fpr" {print $10}')"
  [[ "$fingerprints" == "$(printf '%s\n%s' "$PRIMARY_FINGERPRINT" "$ENCRYPTION_FINGERPRINT")" ]] ||
    fail "committed release public key has an unexpected fingerprint set"
  listing="$("$GPG" --homedir "$inspection_home" --no-options --batch --with-colons \
    --list-keys "$PRIMARY_FINGERPRINT")"
  [[ "$(/usr/bin/awk -F: '$1 == "pub" {print $4 ":" $12}' <<<"$listing")" == '22:'* ]]
  [[ "$(/usr/bin/awk -F: '$1 == "sub" {print $4 ":" $12}' <<<"$listing")" == '18:'*e* ]] ||
    fail "committed release key lacks the exact Curve25519 encryption subkey"
}

prepare_isolated_secret_key_home() {
  local source_private="$RELEASE_GNUPGHOME/private-keys-v1.d"
  local source_primary="$source_private/$PRIMARY_KEYGRIP.key"
  local source_encryption="$source_private/$ENCRYPTION_KEYGRIP.key"
  local actual_entries=''
  local forbidden_config=''
  require_designated_private_directory \
    '/Users/sujithsampath/Library/Application Support/PatrolGrid' '700' \
    'fixed PatrolGrid key root'
  require_designated_private_directory "$RELEASE_GNUPGHOME" '700' \
    'fixed PatrolGrid source GNUPGHOME'
  for forbidden_config in gpg.conf common.conf gpg-agent.conf dirmngr.conf; do
    [[ ! -e "$RELEASE_GNUPGHOME/$forbidden_config" &&
        ! -L "$RELEASE_GNUPGHOME/$forbidden_config" ]] ||
      fail "persistent PatrolGrid GNUPGHOME must not contain executable GnuPG configuration"
  done
  require_designated_private_directory "$source_private" '700' \
    'fixed PatrolGrid secret-key directory'
  actual_entries="$(/usr/bin/find "$source_private" -mindepth 1 -maxdepth 1 -print |
    /usr/bin/sed "s#^$source_private/##" | /usr/bin/sort)"
  [[ "$actual_entries" == "$(/usr/bin/printf '%s\n%s' \
      "$PRIMARY_KEYGRIP.key" "$ENCRYPTION_KEYGRIP.key" | /usr/bin/sort)" ]] ||
    fail "fixed PatrolGrid secret-key directory has an unexpected allowlist"
  for source_key in "$source_primary" "$source_encryption"; do
    require_designated_private_file "$source_key" '600' 'fixed PatrolGrid secret-key file'
  done
  SECRET_GNUPGHOME="$TEMP_ROOT/isolated-secret-gpg"
  "$INSTALL" -d -m 0700 "$SECRET_GNUPGHOME" "$SECRET_GNUPGHOME/private-keys-v1.d"
  "$INSTALL" -m 0600 "$source_primary" "$SECRET_GNUPGHOME/private-keys-v1.d/"
  "$INSTALL" -m 0600 "$source_encryption" "$SECRET_GNUPGHOME/private-keys-v1.d/"
  /bin/chmod -N "$SECRET_GNUPGHOME" "$SECRET_GNUPGHOME/private-keys-v1.d" \
    "$SECRET_GNUPGHOME/private-keys-v1.d/$PRIMARY_KEYGRIP.key" \
    "$SECRET_GNUPGHOME/private-keys-v1.d/$ENCRYPTION_KEYGRIP.key" ||
    fail "could not clear inherited ACLs from the isolated secret-key snapshot"
  require_designated_private_directory "$SECRET_GNUPGHOME" '700' \
    'isolated PatrolGrid GNUPGHOME'
  require_designated_private_directory "$SECRET_GNUPGHOME/private-keys-v1.d" '700' \
    'isolated PatrolGrid secret-key directory'
  require_designated_private_file "$SECRET_GNUPGHOME/private-keys-v1.d/$PRIMARY_KEYGRIP.key" \
    '600' 'isolated PatrolGrid primary key'
  require_designated_private_file "$SECRET_GNUPGHOME/private-keys-v1.d/$ENCRYPTION_KEYGRIP.key" \
    '600' 'isolated PatrolGrid encryption key'
  "$GPG" --homedir "$SECRET_GNUPGHOME" --no-options --agent-program "$GPG_AGENT" \
    --batch --no-auto-key-retrieve --import "$PUBLIC_KEY_FILE" >/dev/null 2>&1
}

verify_secret_decryption_key() {
  local listing=''
  local fingerprints=''
  fingerprints="$("$GPG" --homedir "$SECRET_GNUPGHOME" --no-options \
    --agent-program "$GPG_AGENT" --batch --with-colons \
    --fingerprint --fingerprint --list-secret-keys "$PRIMARY_FINGERPRINT" |
    /usr/bin/awk -F: '$1 == "fpr" {print $10}')"
  [[ "$fingerprints" == "$(printf '%s\n%s' "$PRIMARY_FINGERPRINT" "$ENCRYPTION_FINGERPRINT")" ]] ||
    fail "fixed GNUPGHOME does not hold the exact release secret-key pair"
  listing="$("$GPG" --homedir "$SECRET_GNUPGHOME" --no-options \
    --agent-program "$GPG_AGENT" --batch --with-colons \
    --list-secret-keys "$PRIMARY_FINGERPRINT")"
  [[ "$(/usr/bin/awk -F: '$1 == "ssb" {print $4 ":" $12 ":" $15}' <<<"$listing")" == '18:'*e* ]] ||
    fail "exact secret encryption subkey is unavailable"
}

verify_certificate_and_keystore() {
  local certificate_digest=''
  local keystore_certificate_digest=''
  local listing="$TEMP_ROOT/keystore-list.txt"
  local exported_certificate="$TEMP_ROOT/keystore-certificate.pem"
  require_regular_private_file "$CERTIFICATE_FILE"
  certificate_digest="$("$OPENSSL" x509 -in "$CERTIFICATE_FILE" -outform DER |
    "$SHASUM" -a 256 | /usr/bin/awk '{print tolower($1)}')"
  [[ "$certificate_digest" == "$APK_CERT_SHA256" ]] ||
    fail "committed PatrolGrid APK certificate does not match the pinned digest"
  require_designated_private_directory \
    '/Users/sujithsampath/Library/Application Support/PatrolGrid' '700' \
    'fixed PatrolGrid key root'
  require_designated_private_directory \
    '/Users/sujithsampath/Library/Application Support/PatrolGrid/signing' '700' \
    'fixed PatrolGrid signing directory'
  require_designated_private_file "$RELEASE_KEYSTORE" '600' 'fixed PatrolGrid PKCS#12 keystore'
  keychain_item_exists "$APK_SIGNING_SERVICE"
  "$SECURITY" find-generic-password -w -s "$APK_SIGNING_SERVICE" -a "$KEYCHAIN_ACCOUNT" |
    "$KEYTOOL" -list -v -keystore "$RELEASE_KEYSTORE" -storetype PKCS12 \
      -alias "$RELEASE_KEY_ALIAS" > "$listing" 2>/dev/null ||
    fail "Keychain password cannot open the fixed PKCS#12/alias"
  /usr/bin/grep -q '^Entry type: PrivateKeyEntry$' "$listing" ||
    fail "fixed PKCS#12 alias is not a private-key entry"
  "$SECURITY" find-generic-password -w -s "$APK_SIGNING_SERVICE" -a "$KEYCHAIN_ACCOUNT" |
    "$KEYTOOL" -exportcert -rfc -keystore "$RELEASE_KEYSTORE" -storetype PKCS12 \
      -alias "$RELEASE_KEY_ALIAS" > "$exported_certificate" 2>/dev/null ||
    fail "could not export the fixed alias certificate for verification"
  keystore_certificate_digest="$("$OPENSSL" x509 -in "$exported_certificate" -outform DER |
    "$SHASUM" -a 256 | /usr/bin/awk '{print tolower($1)}')"
  [[ "$keystore_certificate_digest" == "$APK_CERT_SHA256" ]] ||
    fail "fixed PKCS#12 alias certificate does not match the committed certificate"
}

preflight() {
  initialize_tools
  umask 077
  # GnuPG Unix-domain socket paths have a short platform limit; use the fixed,
  # owner-only /tmp namespace instead of macOS's much longer per-user TMPDIR.
  TEMP_ROOT="$("$MKTEMP" -d '/tmp/.patrolgrid-release.XXXXXX')"
  [[ -d "$TEMP_ROOT" && ! -L "$TEMP_ROOT" ]] || fail "could not create private temporary directory"
  verify_toolchain_integrity
  verify_public_key
  prepare_isolated_secret_key_home
  verify_secret_decryption_key
  verify_certificate_and_keystore
  keychain_item_exists "$DECRYPTION_SERVICE"
  keychain_item_exists "$APK_SIGNING_SERVICE"
  "$GH" auth status --hostname github.com >/dev/null 2>&1 ||
    fail "GitHub CLI is not authenticated to github.com"
}

prepare_new_path() {
  local requested="$1"
  local kind="$2"
  local parent=''
  local leaf=''
  [[ ! -e "$requested" && ! -L "$requested" ]] || fail "$kind already exists: $requested"
  parent="$(dirname "$requested")"
  leaf="$(basename "$requested")"
  [[ -n "$leaf" && "$leaf" != '.' && "$leaf" != '..' ]] || fail "invalid $kind path"
  [[ -d "$parent" && ! -L "$parent" ]] || fail "$kind parent is missing or symlinked"
  parent="$(cd "$parent" && pwd -P)"
  [[ "$parent" != '/' ]] || fail "$kind parent must not be the filesystem root"
  [[ "$($STAT -f '%Su' "$parent")" == 'sujithsampath' ]] ||
    fail "$kind parent must be owned by the release account"
  local parent_mode=''
  parent_mode="$($STAT -f '%Lp' "$parent")"
  (( (8#$parent_mode & 022) == 0 )) || fail "$kind parent must not be group/world-writable"
  require_no_extended_acl "$parent"
  PREPARED_BUNDLE_DIR="$parent/$leaf"
}

github_get() {
  local endpoint="$1"
  local output="$2"
  "$GH" api --method GET \
    --hostname github.com \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "$endpoint" > "$output"
}

github_download_bounded() {
  local endpoint="$1"
  local output="$2"
  local maximum="$3"
  local accept="$4"
  case "$accept" in
    # GitHub's Actions ZIP endpoint rejects octet-stream (415); release assets
    # require it.  Keep the only two endpoint-specific media types explicit.
    application/vnd.github+json|application/octet-stream) ;;
    *) fail "unallowlisted authenticated GitHub download media type" ;;
  esac
  [[ ! -e "$output" && ! -L "$output" ]] || fail "bounded download target already exists"
  if ! "$GH" api --method GET --hostname github.com \
      -H "Accept: $accept" -H 'X-GitHub-Api-Version: 2022-11-28' \
      "$endpoint" |
    "$PYTHON" -I -c '
import os
import sys
output_name, maximum_text = sys.argv[1:]
maximum = int(maximum_text)
flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
descriptor = os.open(output_name, flags, 0o600)
written = 0
try:
    with os.fdopen(descriptor, "wb") as output:
        while True:
            chunk = sys.stdin.buffer.read(1024 * 1024)
            if not chunk:
                break
            written += len(chunk)
            if written > maximum:
                raise SystemExit("authenticated GitHub download exceeded its fixed size bound")
            output.write(chunk)
        output.flush()
        os.fsync(output.fileno())
except BaseException:
    try:
        os.unlink(output_name)
    except FileNotFoundError:
        pass
    raise
if written <= 0:
    os.unlink(output_name)
    raise SystemExit("authenticated GitHub download was empty")
' "$output" "$maximum"; then
    fail "authenticated GitHub download failed or exceeded its size bound"
  fi
  require_regular_private_file "$output"
}

verify_monotonic_tag_version() {
  local current_tag="$1"
  local current_code="$2"
  local expected_current_count="$3"
  local remote_tag=''
  local other_major=''
  local other_minor=''
  local other_patch=''
  local other_code=''
  local current_count=0
  local page=1
  local page_count=0
  local page_file=''
  # Never allow gh's implicit pagination to accumulate an unbounded repository
  # response before the private ceremony.  A full page at the fixed bound is
  # itself fail-closed: uniqueness/monotonicity would otherwise be unknowable.
  while (( page <= 1000 )); do
    page_file="$TEMP_ROOT/version-tags-$page.json"
    github_get "/repos/$REPOSITORY/tags?per_page=100&page=$page" "$page_file"
    "$JQ" -e 'type == "array" and length <= 100' "$page_file" >/dev/null ||
      fail "GitHub returned an invalid bounded tag page"
    page_count="$("$JQ" 'length' "$page_file")"
    while IFS= read -r remote_tag; do
      [[ -n "$remote_tag" ]] || continue
      case "$remote_tag" in
        patrolgrid-v*) ;;
        *) continue ;;
      esac
      [[ "$remote_tag" =~ ^patrolgrid-v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] ||
        fail "repository contains a malformed PatrolGrid version tag: $remote_tag"
      if [[ "$remote_tag" == "$current_tag" ]]; then
        current_count=$((current_count + 1))
        continue
      fi
      other_major="${BASH_REMATCH[1]}"
      other_minor="${BASH_REMATCH[2]}"
      other_patch="${BASH_REMATCH[3]}"
      (( other_minor <= 99 && other_patch <= 99 )) ||
        fail "repository contains a PatrolGrid tag outside the version-code scheme: $remote_tag"
      other_code=$((10#$other_major * 10000 + 10#$other_minor * 100 + 10#$other_patch))
      (( other_code >= 1 && other_code <= 2100000000 && current_code > other_code )) ||
        fail "release versionCode must be greater than every prior immutable PatrolGrid tag"
    done < <("$JQ" -r '.[].name' "$page_file")
    (( page_count < 100 )) && break
    (( page < 1000 )) || fail "repository tag history exceeds the fixed 1000-page release bound"
    page=$((page + 1))
  done
  [[ "$current_count" == "$expected_current_count" ]] ||
    fail "current PatrolGrid tag presence does not match the release stage"
}

verify_rulesets() {
  # Security controls are meaningful only when an explicitly identified owner
  # is reading them.  A GitHub Actions token or a different local gh profile
  # must fail before it can authorize a private signing ceremony.
  github_get "/user" "$TEMP_ROOT/authenticated-owner.json"
  "$JQ" -e '.login == "sampathmannam" and .id == 283784163 and .type == "User"' \
    "$TEMP_ROOT/authenticated-owner.json" >/dev/null ||
    fail "GitHub CLI is not authenticated as the PatrolGrid repository owner"
  github_get "/repos/$REPOSITORY/collaborators/sampathmannam/permission" \
    "$TEMP_ROOT/owner-permission.json"
  "$JQ" -e '.user.login == "sampathmannam" and .user.id == 283784163 and
    .user.type == "User" and .permission == "admin"' "$TEMP_ROOT/owner-permission.json" >/dev/null ||
    fail "authenticated PatrolGrid owner does not retain repository admin authority"
  github_get "/repos/$REPOSITORY" "$TEMP_ROOT/repository.json"
  "$JQ" -e '
    .security_and_analysis.secret_scanning.status == "enabled" and
    .security_and_analysis.secret_scanning_push_protection.status == "enabled" and
    .security_and_analysis.dependabot_security_updates.status == "enabled"' \
    "$TEMP_ROOT/repository.json" >/dev/null || fail "repository security-analysis settings drifted"
  "$GH" api --method GET --include --hostname github.com \
    -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
    "/repos/$REPOSITORY/vulnerability-alerts" > "$TEMP_ROOT/vulnerability-alerts-response.txt"
  /usr/bin/grep -Eq '^HTTP/[0-9.]+ 204([[:space:]]|$)' \
    "$TEMP_ROOT/vulnerability-alerts-response.txt" || fail "vulnerability alerts are disabled"
  github_get "/repos/$REPOSITORY/automated-security-fixes" \
    "$TEMP_ROOT/security-updates.json"
  "$JQ" -e '.enabled == true and .paused == false' "$TEMP_ROOT/security-updates.json" >/dev/null ||
    fail "Dependabot security updates are disabled or paused"
  github_get "/repos/$REPOSITORY/private-vulnerability-reporting" \
    "$TEMP_ROOT/private-vulnerability-reporting.json"
  "$JQ" -e '.enabled == true' "$TEMP_ROOT/private-vulnerability-reporting.json" >/dev/null ||
    fail "private vulnerability reporting is disabled"

  github_get "/repos/$REPOSITORY/rulesets/22066728" "$TEMP_ROOT/main-ruleset.json"
  "$JQ" -e '
    .id == 22066728 and .name == "PatrolGrid protected main" and
    .target == "branch" and .enforcement == "active" and
    .conditions.ref_name == {include:["refs/heads/main"],exclude:[]} and
    .bypass_actors == [] and
    ([.rules[].type]|sort) ==
      (["deletion","non_fast_forward","pull_request","required_linear_history",
        "required_signatures","required_status_checks"]|sort) and
    ([.rules[]|select(.type=="pull_request")|.parameters] == [{
      allowed_merge_methods:["squash"],dismiss_stale_reviews_on_push:false,
      require_code_owner_review:false,
      require_extra_approval_for_unattributed_changes:true,
      require_last_push_approval:false,required_approving_review_count:0,
      required_review_thread_resolution:true,required_reviewers:[]}]) and
    ([.rules[]|select(.type=="required_status_checks")|.parameters] == [{
      do_not_enforce_on_create:false,
      required_status_checks:[{context:"build",integration_id:15368},
        {context:"patrolgrid-backend",integration_id:15368},
        {context:"dependency-review",integration_id:15368},
        {context:"codeql",integration_id:15368}],
      strict_required_status_checks_policy:true}])' \
    "$TEMP_ROOT/main-ruleset.json" >/dev/null || fail "protected-main ruleset drifted"
  github_get "/repos/$REPOSITORY/rulesets/22066729" "$TEMP_ROOT/tag-creator-ruleset.json"
  "$JQ" -e '
    .id == 22066729 and .name == "PatrolGrid release tag creator" and
    .target == "tag" and .enforcement == "active" and
    .conditions.ref_name == {include:["refs/tags/patrolgrid-v*"],exclude:[]} and
    [.rules[].type] == ["creation"] and
    .bypass_actors == [{"actor_id":283784163,"actor_type":"User","bypass_mode":"always"}]' \
    "$TEMP_ROOT/tag-creator-ruleset.json" >/dev/null || fail "release-tag creator ruleset drifted"
  github_get "/repos/$REPOSITORY/rulesets/22066730" "$TEMP_ROOT/tag-immutable-ruleset.json"
  "$JQ" -e '
    .id == 22066730 and .name == "PatrolGrid immutable release tags" and
    .target == "tag" and .enforcement == "active" and
    .conditions.ref_name == {include:["refs/tags/patrolgrid-v*"],exclude:[]} and
    ([.rules[].type]|sort) == (["update","deletion","non_fast_forward"]|sort) and
    .bypass_actors == []' "$TEMP_ROOT/tag-immutable-ruleset.json" >/dev/null ||
    fail "immutable release-tag ruleset drifted"

  github_get "/repos/$REPOSITORY/actions/permissions" "$TEMP_ROOT/actions-policy.json"
  "$JQ" -e '.enabled == true and .allowed_actions == "selected" and
    .sha_pinning_required == true' "$TEMP_ROOT/actions-policy.json" >/dev/null ||
    fail "repository Actions execution policy drifted"
  github_get "/repos/$REPOSITORY/actions/permissions/selected-actions" \
    "$TEMP_ROOT/actions-allowlist.json"
  "$JQ" -e '.github_owned_allowed == true and .verified_allowed == false and
    (.patterns_allowed|sort) == (["android-actions/setup-android@*",
      "anchore/sbom-action@*","gradle/actions@*",
      "reactivecircus/android-emulator-runner@*","supabase/setup-cli@*"]|sort)' \
    "$TEMP_ROOT/actions-allowlist.json" >/dev/null || fail "repository Actions allowlist drifted"

  github_get "/repos/$REPOSITORY/environments/patrolgrid-production" \
    "$TEMP_ROOT/release-environment.json"
  "$JQ" -e '.name == "patrolgrid-production" and
    .deployment_branch_policy == {protected_branches:false,custom_branch_policies:true} and
    ([.protection_rules[].type]|sort) == (["branch_policy","required_reviewers"]|sort) and
    ([.protection_rules[]|select(.type=="required_reviewers")|
      {prevent_self_review,reviewers:[.reviewers[]|{type,reviewer:{id:.reviewer.id,
        login:.reviewer.login,type:.reviewer.type}}]}] == [{prevent_self_review:false,
          reviewers:[{type:"User",reviewer:{id:283784163,login:"sampathmannam",type:"User"}}]}])' \
    "$TEMP_ROOT/release-environment.json" >/dev/null || fail "release environment policy drifted"
  github_get "/repos/$REPOSITORY/environments/patrolgrid-production/deployment-branch-policies" \
    "$TEMP_ROOT/release-environment-tags.json"
  "$JQ" -e '.total_count == 1 and
    [.branch_policies[]|{name,type}] == [{name:"patrolgrid-v*",type:"tag"}]' \
    "$TEMP_ROOT/release-environment-tags.json" >/dev/null ||
    fail "release environment tag policy drifted"
}

verify_exact_tag_signature() {
  local tag_json="$1"
  local verification_home="$TEMP_ROOT/tag-verification-gpg"
  local signature="$TEMP_ROOT/tag-signature.asc"
  local payload="$TEMP_ROOT/tag-payload"
  local status="$TEMP_ROOT/tag-signature-status"
  local valid_fingerprints=''
  "$INSTALL" -d -m 0700 "$verification_home"
  "$GPG" --homedir "$verification_home" --no-options --batch --no-auto-key-retrieve \
    --import "$PUBLIC_KEY_FILE" >/dev/null 2>&1
  "$JQ" -j '.verification.signature' "$tag_json" > "$signature"
  "$JQ" -j '.verification.payload' "$tag_json" > "$payload"
  require_regular_private_file "$signature"
  require_regular_private_file "$payload"
  "$GPG" --homedir "$verification_home" --no-options --batch --no-auto-key-retrieve \
    --status-fd 1 --verify "$signature" "$payload" > "$status" 2>/dev/null ||
    fail "release tag signature is not valid under the committed PatrolGrid key"
  valid_fingerprints="$(/usr/bin/awk \
    '$1 == "[GNUPG:]" && $2 == "VALIDSIG" {print $3}' "$status")"
  [[ "$valid_fingerprints" == "$PRIMARY_FINGERPRINT" ]] ||
    fail "release tag was not signed by the exact PatrolGrid primary key"
}

reverify_governance_before_private_signing() {
  local tag="$1"
  local version_code="$2"
  local commit="$3"
  local tag_object_sha=''
  verify_rulesets
  verify_monotonic_tag_version "$tag" "$version_code" 1
  github_get "/repos/$REPOSITORY/git/ref/heads/main" "$TEMP_ROOT/final-main-ref.json"
  [[ "$("$JQ" -r '.object.sha' "$TEMP_ROOT/final-main-ref.json")" == "$commit" ]] ||
    fail "protected main changed before private signing"
  github_get "/repos/$REPOSITORY/git/ref/tags/$tag" "$TEMP_ROOT/final-tag-ref.json"
  tag_object_sha="$("$JQ" -r '.object.sha' "$TEMP_ROOT/final-tag-ref.json")"
  [[ "$tag_object_sha" =~ ^[0-9a-f]{40}$ ]] || fail "release tag changed before private signing"
  "$JQ" -e --arg ref "refs/tags/$tag" '.ref == $ref and .object.type == "tag"' \
    "$TEMP_ROOT/final-tag-ref.json" >/dev/null || fail "release tag is no longer annotated"
  github_get "/repos/$REPOSITORY/git/tags/$tag_object_sha" "$TEMP_ROOT/final-tag.json"
  "$JQ" -e --arg tag "$tag" --arg commit "$commit" \
    '.tag == $tag and .object.type == "commit" and .object.sha == $commit and
     .verification.verified == true and .verification.reason == "valid"' \
    "$TEMP_ROOT/final-tag.json" >/dev/null || fail "release tag changed before private signing"
  verify_exact_tag_signature "$TEMP_ROOT/final-tag.json"
}

verify_draft_asset_is_exact_actions_artifact() {
  local commit="$1"
  local artifact_container_name="$2"
  local expected_member_name="$3"
  local artifact_zip="$TEMP_ROOT/actions-artifact.zip"
  local extracted="$TEMP_ROOT/actions-artifact-ciphertext"

  if [[ ! "$RESOLVED_RELEASE_BODY" =~ ^Encrypted\ unsigned\ PatrolGrid\ candidate\.\ Workflow\ run\ ([1-9][0-9]*)\;\ source\ commit\ ($commit)\;\ Actions\ artifact\ ([1-9][0-9]*)\;\ artifact\ digest\ (sha256:[0-9a-f]{64})\;\ ciphertext\ digest\ ($RESOLVED_ASSET_DIGEST)\.\ Decrypt\ and\ sign\ only\ with\ the\ repository\'s\ Mac\ release\ ceremony\.\ Never\ publish\ this\ draft\.$ ]]; then
    fail "draft body does not bind exact workflow/artifact/ciphertext provenance"
  fi
  RESOLVED_WORKFLOW_RUN_ID="${BASH_REMATCH[1]}"
  RESOLVED_ACTIONS_ARTIFACT_ID="${BASH_REMATCH[3]}"
  RESOLVED_ACTIONS_ARTIFACT_DIGEST="${BASH_REMATCH[4]}"

  github_get "/repos/$REPOSITORY/actions/artifacts/$RESOLVED_ACTIONS_ARTIFACT_ID" \
    "$TEMP_ROOT/workflow-artifact.json"
  "$JQ" -e --argjson id "$RESOLVED_ACTIONS_ARTIFACT_ID" \
    --argjson run "$RESOLVED_WORKFLOW_RUN_ID" --arg name "$artifact_container_name" \
    --arg digest "$RESOLVED_ACTIONS_ARTIFACT_DIGEST" --arg sha "$commit" \
    --argjson maximum "$((MAX_CIPHERTEXT_BYTES + 16777216))" \
    '.id == $id and .name == $name and .digest == $digest and .expired == false and
     .workflow_run.id == $run and .workflow_run.head_sha == $sha and
     .size_in_bytes > 64 and .size_in_bytes <= $maximum' \
    "$TEMP_ROOT/workflow-artifact.json" >/dev/null ||
    fail "draft does not identify the exact immutable workflow artifact"

  github_download_bounded "/repos/$REPOSITORY/actions/artifacts/$RESOLVED_ACTIONS_ARTIFACT_ID/zip" \
    "$artifact_zip" "$((MAX_CIPHERTEXT_BYTES + 16777216))" 'application/vnd.github+json'
  (( $(size_file "$artifact_zip") <= MAX_CIPHERTEXT_BYTES + 16777216 )) ||
    fail "Actions artifact ZIP exceeds the bounded ciphertext size"
  [[ "sha256:$(sha256_file "$artifact_zip")" == "$RESOLVED_ACTIONS_ARTIFACT_DIGEST" ]] ||
    fail "downloaded Actions artifact ZIP digest does not match its immutable record"

  "$PYTHON" -I - "$artifact_zip" "$expected_member_name" "$extracted" \
    "$MAX_CIPHERTEXT_BYTES" <<'PY'
import os
from pathlib import PurePosixPath
import stat
import struct
import sys
import zipfile

archive_name, expected_name, output_name, maximum_text = sys.argv[1:]
maximum = int(maximum_text)
archive_size = os.path.getsize(archive_name)
with open(archive_name, "rb") as source:
    source.seek(max(0, archive_size - 65557))
    tail = source.read()
offset = tail.rfind(b"PK\x05\x06")
if offset < 0 or len(tail) - offset < 22:
    raise SystemExit("Actions artifact ZIP has no bounded end record")
_, disk, directory_disk, disk_entries, total_entries, directory_size, directory_offset, comment_size = \
    struct.unpack_from("<4s4H2LH", tail, offset)
if (disk != 0 or directory_disk != 0 or disk_entries != 1 or total_entries != 1
        or comment_size != len(tail) - offset - 22
        or directory_size > 1048576 or directory_offset + directory_size > archive_size):
    raise SystemExit("Actions artifact ZIP central-directory allowlist is invalid")
with zipfile.ZipFile(archive_name) as archive:
    members = archive.infolist()
    if len(members) != 1:
        raise SystemExit("Actions artifact ZIP must contain exactly one entry")
    member = members[0]
    path = PurePosixPath(member.filename)
    mode = (member.external_attr >> 16) & 0xFFFF
    if (member.filename != expected_name or path.is_absolute() or ".." in path.parts
            or member.is_dir() or (mode and not stat.S_ISREG(mode))
            or member.flag_bits & 0x1 or not 64 < member.file_size <= maximum
            or member.compress_size <= 0
            or member.file_size > member.compress_size * 2 + 1024):
        raise SystemExit("Actions artifact ZIP entry is unsafe or unexpected")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(output_name, flags, 0o600)
    written = 0
    try:
        with os.fdopen(descriptor, "wb") as destination, archive.open(member) as source:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if written > member.file_size:
                    raise SystemExit("Actions artifact ZIP expanded past its declared size")
                destination.write(chunk)
            destination.flush()
            os.fsync(destination.fileno())
    except BaseException:
        try:
            os.unlink(output_name)
        except FileNotFoundError:
            pass
        raise
    if written != member.file_size:
        os.unlink(output_name)
        raise SystemExit("Actions artifact ZIP entry was truncated")
PY
  require_regular_private_file "$extracted"
  [[ "sha256:$(sha256_file "$extracted")" == "$RESOLVED_ASSET_DIGEST" ]] ||
    fail "Actions artifact ciphertext digest does not match the draft asset record"
  /usr/bin/cmp -s "$extracted" "$TEMP_ROOT/$RESOLVED_ASSET_NAME" ||
    fail "draft asset is not byte-for-byte the exact successful Actions artifact"
}

resolve_authenticated_draft() {
  local tag="$1"
  local version="$2"
  local tag_object_sha=''
  local main_sha=''
  local asset_name=''
  local asset_id=''
  local asset_digest=''

  github_get "/repos/$REPOSITORY" "$TEMP_ROOT/repository.json"
  "$JQ" -e '.private == false and .default_branch == "main" and
    .allow_squash_merge == true and .allow_merge_commit == false and
    .allow_rebase_merge == false' \
    "$TEMP_ROOT/repository.json" >/dev/null || fail "unexpected GitHub repository identity"
  github_get "/repos/$REPOSITORY/branches/main" "$TEMP_ROOT/main-branch.json"
  "$JQ" -e '.name == "main" and .protected == true' "$TEMP_ROOT/main-branch.json" >/dev/null ||
    fail "main is not protected"
  verify_rulesets

  github_get "/repos/$REPOSITORY/git/ref/tags/$tag" "$TEMP_ROOT/tag-ref.json"
  tag_object_sha="$("$JQ" -r '.object.sha' "$TEMP_ROOT/tag-ref.json")"
  "$JQ" -e --arg ref "refs/tags/$tag" '.ref == $ref and .object.type == "tag"' \
    "$TEMP_ROOT/tag-ref.json" >/dev/null || fail "release tag is not annotated"
  [[ "$tag_object_sha" =~ ^[0-9a-f]{40}$ ]] || fail "invalid tag-object SHA"
  github_get "/repos/$REPOSITORY/git/tags/$tag_object_sha" "$TEMP_ROOT/tag.json"
  "$JQ" -e --arg tag "$tag" \
    '.tag == $tag and .object.type == "commit" and
     (.object.sha|test("^[0-9a-f]{40}$")) and
     .verification.verified == true and .verification.reason == "valid"' \
    "$TEMP_ROOT/tag.json" >/dev/null || fail "GitHub did not verify the annotated tag signature"
  verify_exact_tag_signature "$TEMP_ROOT/tag.json"
  RESOLVED_COMMIT="$("$JQ" -r '.object.sha' "$TEMP_ROOT/tag.json")"

  github_get "/repos/$REPOSITORY/git/ref/heads/main" "$TEMP_ROOT/main-ref.json"
  main_sha="$("$JQ" -r '.object.sha' "$TEMP_ROOT/main-ref.json")"
  [[ "$main_sha" =~ ^[0-9a-f]{40}$ ]] || fail "invalid live main SHA"
  [[ "$main_sha" == "$RESOLVED_COMMIT" ]] ||
    fail "release tag must target the current protected main HEAD"
  github_get "/repos/$REPOSITORY/compare/$RESOLVED_COMMIT...$main_sha" "$TEMP_ROOT/compare.json"
  "$JQ" -e --arg commit "$RESOLVED_COMMIT" --arg main "$main_sha" \
    '.base_commit.sha == $commit and .merge_base_commit.sha == $commit and
     .head_commit.sha == $main and .status == "identical"' \
    "$TEMP_ROOT/compare.json" >/dev/null || fail "tagged commit is not exact live main HEAD"

  /usr/bin/printf '[]\n' > "$TEMP_ROOT/releases.json"
  local release_page=1
  local release_page_count=0
  local release_page_file=''
  while (( release_page <= 1000 )); do
    release_page_file="$TEMP_ROOT/releases-$release_page.json"
    github_get "/repos/$REPOSITORY/releases?per_page=100&page=$release_page" "$release_page_file"
    "$JQ" -e 'type == "array" and length <= 100' "$release_page_file" >/dev/null ||
      fail "GitHub returned an invalid bounded release page"
    release_page_count="$("$JQ" 'length' "$release_page_file")"
    "$JQ" --arg tag "$tag" --slurpfile previous "$TEMP_ROOT/releases.json" \
      '$previous[0] + [.[] | select(.tag_name == $tag)]' "$release_page_file" \
      > "$TEMP_ROOT/releases-next.json"
    /bin/mv -f -- "$TEMP_ROOT/releases-next.json" "$TEMP_ROOT/releases.json"
    (( release_page_count < 100 )) && break
    (( release_page < 1000 )) || fail "repository release history exceeds the fixed 1000-page release bound"
    release_page=$((release_page + 1))
  done
  [[ "$("$JQ" 'length' "$TEMP_ROOT/releases.json")" == '1' ]] ||
    fail "exactly one authenticated draft must exist for $tag"
  "$JQ" '.[0]' "$TEMP_ROOT/releases.json" > "$TEMP_ROOT/release.json"
  asset_name="PatrolGrid-$version-$RESOLVED_COMMIT-unsigned-candidate.tar.gpg"
  "$JQ" -e --arg tag "$tag" --arg asset "$asset_name" --argjson maximum "$MAX_CIPHERTEXT_BYTES" \
    '.tag_name == $tag and .draft == true and
     .prerelease == false and .published_at == null and (.assets|length) == 1 and
     .assets[0].name == $asset and .assets[0].state == "uploaded" and
     .assets[0].uploader.login == "github-actions[bot]" and
     .assets[0].size > 64 and .assets[0].size <= $maximum and
     (.assets[0].digest|test("^sha256:[0-9a-f]{64}$"))' \
    "$TEMP_ROOT/release.json" >/dev/null || fail "draft is not the exact ciphertext-only release"
  asset_id="$("$JQ" -r '.assets[0].id' "$TEMP_ROOT/release.json")"
  asset_digest="$("$JQ" -r '.assets[0].digest' "$TEMP_ROOT/release.json")"
  [[ "$asset_id" =~ ^[1-9][0-9]*$ ]] || fail "invalid release asset id"
  RESOLVED_ASSET_NAME="$asset_name"
  RESOLVED_ASSET_DIGEST="$asset_digest"
  RESOLVED_RELEASE_ID="$("$JQ" -r '.id' "$TEMP_ROOT/release.json")"
  RESOLVED_RELEASE_ASSET_ID="$asset_id"
  [[ "$RESOLVED_RELEASE_ID" =~ ^[1-9][0-9]*$ ]] || fail "invalid draft release id"
  RESOLVED_RELEASE_BODY="$("$JQ" -r '.body' "$TEMP_ROOT/release.json")"
  github_download_bounded "/repos/$REPOSITORY/releases/assets/$asset_id" \
    "$TEMP_ROOT/$asset_name" "$MAX_CIPHERTEXT_BYTES" 'application/octet-stream'
  [[ "sha256:$(sha256_file "$TEMP_ROOT/$asset_name")" == "$asset_digest" ]] ||
    fail "downloaded release asset digest does not match GitHub metadata"
  verify_draft_asset_is_exact_actions_artifact "$RESOLVED_COMMIT" \
    "PatrolGrid-$version-$RESOLVED_COMMIT-candidate-ciphertext" "$RESOLVED_ASSET_NAME"
}

decrypt_candidate() {
  local ciphertext="$1"
  local archive="$2"
  local status_file="$TEMP_ROOT/gpg-status.txt"
  local actual_ciphertext_size=''
  actual_ciphertext_size="$(size_file "$ciphertext")"
  (( actual_ciphertext_size > 64 && actual_ciphertext_size <= MAX_CIPHERTEXT_BYTES )) ||
    fail "ciphertext exceeds the bounded release-candidate size"
  "$PYTHON" -I "$PACKET_VERIFIER" "$ciphertext" "$ENCRYPTION_KEY_ID" >/dev/null ||
    fail "ciphertext packet graph is not addressed only to the pinned encryption subkey"
  keychain_item_exists "$DECRYPTION_SERVICE"
  if ! "$SECURITY" find-generic-password -w -s "$DECRYPTION_SERVICE" -a "$KEYCHAIN_ACCOUNT" |
    "$GPG" --homedir "$SECRET_GNUPGHOME" --no-options --agent-program "$GPG_AGENT" \
      --batch --yes --no-auto-key-retrieve \
      --pinentry-mode loopback --passphrase-fd 0 --try-secret-key "$ENCRYPTION_FINGERPRINT!" \
      --status-fd 3 --output - --decrypt "$ciphertext" 3> "$status_file" |
    "$PYTHON" -I -c '
import os
import sys
output_name, maximum_text = sys.argv[1:]
maximum = int(maximum_text)
flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
descriptor = os.open(output_name, flags, 0o600)
written = 0
try:
    with os.fdopen(descriptor, "wb") as destination:
        while True:
            chunk = sys.stdin.buffer.read(1024 * 1024)
            if not chunk:
                break
            written += len(chunk)
            if written > maximum:
                raise SystemExit("decrypted candidate archive exceeds its size bound")
            destination.write(chunk)
        destination.flush()
        os.fsync(destination.fileno())
except BaseException:
    try:
        os.unlink(output_name)
    except FileNotFoundError:
        pass
    raise
if written <= 0:
    os.unlink(output_name)
    raise SystemExit("decrypted candidate archive is empty")
' "$archive" "$MAX_DECRYPTED_ARCHIVE_BYTES"
  then
    fail "candidate could not be decrypted by the exact release subkey"
  fi
  require_regular_private_file "$archive"
  [[ "$(/usr/bin/grep -c '^\[GNUPG:\] ENC_TO ' "$status_file")" == '1' ]] ||
    fail "ciphertext must contain exactly one public-key recipient"
  /usr/bin/grep -q "^\[GNUPG:\] ENC_TO $ENCRYPTION_KEY_ID " "$status_file" ||
    fail "ciphertext was not addressed only to the pinned encryption subkey"
  /usr/bin/grep -q "^\[GNUPG:\] DECRYPTION_KEY $ENCRYPTION_FINGERPRINT $PRIMARY_FINGERPRINT " \
    "$status_file" || fail "GPG used an unexpected decryption key"
  /usr/bin/grep -q '^\[GNUPG:\] DECRYPTION_OKAY$' "$status_file" ||
    fail "GPG did not authenticate a complete decryption"
}

extract_exact_candidate() {
  local archive="$1"
  local version="$2"
  local commit="$3"
  local extracted="$TEMP_ROOT/candidate"
  local unsigned_name="PatrolGrid-$version-$commit-unsigned.apk"
  local mapping_name="PatrolGrid-$version-$commit-mapping.txt"
  local sbom_name="PatrolGrid-$version-$commit.spdx.json"
  local manifest_name="PatrolGrid-$version-$commit-candidate.json"
  "$INSTALL" -d -m 0700 "$extracted"
  "$PYTHON" -I - "$archive" "$extracted" \
    "$unsigned_name" "$MAX_UNSIGNED_APK_BYTES" \
    "$mapping_name" "$MAX_MAPPING_BYTES" \
    "$sbom_name" "$MAX_SBOM_BYTES" \
    "$manifest_name" "$MAX_MANIFEST_BYTES" <<'PY'
import os
from pathlib import PurePosixPath
import sys
import tarfile

archive_name, destination_name, *pairs = sys.argv[1:]
expected = [(pairs[index], int(pairs[index + 1])) for index in range(0, len(pairs), 2)]
destination = os.path.realpath(destination_name)
with tarfile.open(archive_name, mode="r|") as archive:
    for name, maximum in expected:
        member = archive.next()
        if member is None or member.name != name:
            raise SystemExit("candidate archive allowlist/order is invalid")
        path = PurePosixPath(member.name)
        if (not member.isreg() or path.is_absolute() or len(path.parts) != 1
                or ".." in path.parts or member.pax_headers
                or member.uid != 0 or member.gid != 0 or int(member.mtime) != 0
                or not 0 < member.size <= maximum):
            raise SystemExit("candidate archive member type, metadata, or size is unsafe")
        source = archive.extractfile(member)
        if source is None:
            raise SystemExit("candidate archive member cannot be streamed")
        output_name = os.path.join(destination, name)
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(output_name, flags, 0o600)
        written = 0
        try:
            with source, os.fdopen(descriptor, "wb") as output:
                while True:
                    chunk = source.read(1024 * 1024)
                    if not chunk:
                        break
                    written += len(chunk)
                    if written > maximum or written > member.size:
                        raise SystemExit("candidate archive member exceeded its size bound")
                    output.write(chunk)
                output.flush()
                os.fsync(output.fileno())
        except BaseException:
            try:
                os.unlink(output_name)
            except FileNotFoundError:
                pass
            raise
        if written != member.size:
            os.unlink(output_name)
            raise SystemExit("candidate archive member was truncated")
    if archive.next() is not None:
        raise SystemExit("candidate archive allowlist/order is invalid")
PY
  for name in "$unsigned_name" "$mapping_name" "$sbom_name" "$manifest_name"; do
    require_regular_private_file "$extracted/$name"
  done
  CANDIDATE_UNSIGNED="$extracted/$unsigned_name"
  CANDIDATE_MAPPING="$extracted/$mapping_name"
  CANDIDATE_SBOM="$extracted/$sbom_name"
  CANDIDATE_MANIFEST="$extracted/$manifest_name"
}

verify_manifest_and_provenance() {
  local tag="$1"
  local version="$2"
  local version_code="$3"
  local commit="$4"
  local run_id=''
  local run_attempt=''
  local artifact_name="PatrolGrid-$version-$commit-candidate-ciphertext"

  "$JQ" -e --arg tag "$tag" --arg version "$version" --argjson code "$version_code" \
    --arg commit "$commit" --arg repo "$REPOSITORY" \
    'keys == ["android","artifacts","backend","commit","privacyPolicy","releaseTag","repository","schemaVersion","workflow"] and
     .schemaVersion == 1 and .releaseTag == $tag and .commit == $commit and
     .repository == $repo and
     (.android|keys) == ["applicationId","manifestSha256","versionCode","versionName"] and
     .android.applicationId == "com.dailybeat.app.patrolgrid" and
     .android.versionCode == $code and .android.versionName == $version and
     (.android.manifestSha256|test("^[0-9a-f]{64}$")) and
     (.backend|keys) == ["identity","supabaseAnonKeySha256","supabaseUrlSha256"] and
     .backend.identity != "UNCONFIGURED" and (.backend.identity|test("^https://[^/?#]+/?$")) and
     (.backend.supabaseAnonKeySha256|test("^[0-9a-f]{64}$")) and
     (.backend.supabaseUrlSha256|test("^[0-9a-f]{64}$")) and
     (.privacyPolicy|keys) == ["noticeVersion","sha256","status","url"] and
     .privacyPolicy.noticeVersion == 3 and .privacyPolicy.status == "APPROVED" and
     .privacyPolicy.url == ("https://github.com/"+$repo+"/blob/"+$commit+"/docs/PATROLGRID_PRIVACY_POLICY.md") and
     (.privacyPolicy.sha256|test("^[0-9a-f]{64}$")) and
     (.workflow|keys) == ["ref","runAttempt","runId"] and
     .workflow.ref == ($repo+"/.github/workflows/release.yml@refs/tags/"+$tag) and
     .workflow.runId > 0 and .workflow.runAttempt > 0 and
     (.artifacts|keys) == ["mapping","sbom","unsignedApk"] and
     all(.artifacts[]; (keys == ["name","sha256","size"]) and
       (.sha256|test("^[0-9a-f]{64}$")) and .size > 0)' \
    "$CANDIDATE_MANIFEST" >/dev/null || fail "candidate manifest schema/identity is invalid"
  for key in unsignedApk mapping sbom; do
    local path=''
    case "$key" in
      unsignedApk) path="$CANDIDATE_UNSIGNED" ;;
      mapping) path="$CANDIDATE_MAPPING" ;;
      sbom) path="$CANDIDATE_SBOM" ;;
    esac
    [[ "$("$JQ" -r ".artifacts.$key.name" "$CANDIDATE_MANIFEST")" == "$(basename "$path")" ]]
    [[ "$("$JQ" -r ".artifacts.$key.sha256" "$CANDIDATE_MANIFEST")" == "$(sha256_file "$path")" ]]
    [[ "$("$JQ" -r ".artifacts.$key.size" "$CANDIDATE_MANIFEST")" == "$(size_file "$path")" ]]
  done || fail "candidate artifact hash, size, or name does not match its manifest"
  "$JQ" -e '.spdxVersion == "SPDX-2.3" and (.packages|type) == "array"' \
    "$CANDIDATE_SBOM" >/dev/null || fail "candidate SBOM is not valid SPDX JSON"

  run_id="$("$JQ" -r '.workflow.runId' "$CANDIDATE_MANIFEST")"
  run_attempt="$("$JQ" -r '.workflow.runAttempt' "$CANDIDATE_MANIFEST")"
  [[ "$RESOLVED_WORKFLOW_RUN_ID" == "$run_id" ]] ||
    fail "draft provenance disagrees with the decrypted candidate"
  github_get "/repos/$REPOSITORY/actions/runs/$run_id" "$TEMP_ROOT/workflow-run.json"
  "$JQ" -e --argjson id "$run_id" --argjson attempt "$run_attempt" \
    --arg tag "$tag" --arg commit "$commit" --arg repo "$REPOSITORY" \
    '.id == $id and .run_attempt == $attempt and .event == "push" and
     .head_branch == $tag and .head_sha == $commit and .status == "completed" and
     .conclusion == "success" and .path == ".github/workflows/release.yml" and
     .head_repository.full_name == $repo' "$TEMP_ROOT/workflow-run.json" >/dev/null ||
    fail "candidate is not bound to a successful exact release workflow run"
  "$JQ" -e --argjson id "$RESOLVED_ACTIONS_ARTIFACT_ID" --argjson run "$run_id" \
    --arg name "$artifact_name" --arg digest "$RESOLVED_ACTIONS_ARTIFACT_DIGEST" \
    --arg sha "$commit" \
    '.id == $id and .name == $name and .digest == $digest and .expired == false and
     .workflow_run.id == $run and .workflow_run.head_sha == $sha' \
    "$TEMP_ROOT/workflow-artifact.json" >/dev/null ||
    fail "draft ciphertext is not bound to the exact successful workflow artifact"

  github_get "/repos/$REPOSITORY/contents/android/patrolgrid-production.properties?ref=$commit" \
    "$TEMP_ROOT/backend-source.json"
  "$JQ" -e '.type == "file" and .encoding == "base64"' "$TEMP_ROOT/backend-source.json" >/dev/null
  "$JQ" -r '.content' "$TEMP_ROOT/backend-source.json" | tr -d '\n' |
    /usr/bin/base64 --decode > "$TEMP_ROOT/patrolgrid-production.properties"
  local source_url=''
  local source_key_hash=''
  local source_privacy_status=''
  local source_privacy_notice_version=''
  source_url="$(sed -n 's/^SUPABASE_URL=//p' "$TEMP_ROOT/patrolgrid-production.properties")"
  source_key_hash="$(sed -n 's/^SUPABASE_ANON_KEY_SHA256=//p' "$TEMP_ROOT/patrolgrid-production.properties")"
  source_privacy_status="$(sed -n 's/^PRIVACY_POLICY_STATUS=//p' \
    "$TEMP_ROOT/patrolgrid-production.properties")"
  source_privacy_notice_version="$(sed -n 's/^PRIVACY_NOTICE_VERSION=//p' \
    "$TEMP_ROOT/patrolgrid-production.properties")"
  [[ "$source_url" == "$("$JQ" -r '.backend.identity' "$CANDIDATE_MANIFEST")" ]]
  [[ "$(printf '%s' "$source_url" | "$SHASUM" -a 256 | /usr/bin/awk '{print $1}')" == \
      "$("$JQ" -r '.backend.supabaseUrlSha256' "$CANDIDATE_MANIFEST")" ]]
  [[ "$source_key_hash" == "$("$JQ" -r '.backend.supabaseAnonKeySha256' "$CANDIDATE_MANIFEST")" ]]
  [[ "$source_url" != 'UNCONFIGURED' && "$source_key_hash" =~ ^[0-9a-f]{64}$ ]] ||
    fail "candidate backend identity does not match commit-pinned production source"
  [[ "$source_privacy_status" == 'APPROVED' && "$source_privacy_notice_version" == '3' &&
      "$source_privacy_status" == \
        "$("$JQ" -r '.privacyPolicy.status' "$CANDIDATE_MANIFEST")" &&
      "$source_privacy_notice_version" == \
        "$("$JQ" -r '.privacyPolicy.noticeVersion' "$CANDIDATE_MANIFEST")" ]] ||
    fail "candidate privacy approval/version does not match commit-pinned production source"

  github_get "/repos/$REPOSITORY/contents/docs/PATROLGRID_PRIVACY_POLICY.md?ref=$commit" \
    "$TEMP_ROOT/privacy-source.json"
  "$JQ" -r '.content' "$TEMP_ROOT/privacy-source.json" | tr -d '\n' |
    /usr/bin/base64 --decode > "$TEMP_ROOT/PATROLGRID_PRIVACY_POLICY.md"
  [[ "$(sha256_file "$TEMP_ROOT/PATROLGRID_PRIVACY_POLICY.md")" == \
      "$("$JQ" -r '.privacyPolicy.sha256' "$CANDIDATE_MANIFEST")" ]] ||
    fail "privacy-policy source hash does not match the candidate manifest"
}

manifest_metadata_value() {
  local apk="$1"
  local name="$2"
  local xml="$3"
  run_apkanalyzer manifest print "$apk" > "$xml"
  /usr/bin/xmllint --xpath \
    "string(//*[local-name()='meta-data' and @*[local-name()='name']='$name']/@*[local-name()='value'])" \
    "$xml" 2>/dev/null
}

verify_apk_identity() {
  local apk="$1"
  local version="$2"
  local version_code="$3"
  local commit="$4"
  local expected_signed="$5"
  local prefix="$6"
  local package_line=''
  local actual_package=''
  local actual_version=''
  local actual_code=''
  local signer_output=''
  local signer_digests=''
  package_line="$("$AAPT" dump badging "$apk" | sed -n '1p')"
  actual_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$package_line")"
  actual_version="$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$package_line")"
  actual_code="$(sed -n "s/.* versionCode='\([^']*\)'.*/\1/p" <<<"$package_line")"
  [[ "$actual_package" == "$EXPECTED_PACKAGE" && "$actual_version" == "$version" &&
      "$actual_code" == "$version_code" ]] || fail "$prefix package/version identity mismatch"
  [[ "$(run_apkanalyzer manifest debuggable "$apk" | tr -d '\r\n')" == 'false' ]] ||
    fail "$prefix APK is debuggable"
  local apk_commit=''
  local backend=''
  local privacy_status=''
  local privacy_notice_version=''
  local manifest_hash=''
  local resources="$TEMP_ROOT/$prefix-resources.txt"
  local network_policy="$TEMP_ROOT/$prefix-network-security-config.txt"
  local extraction_policy="$TEMP_ROOT/$prefix-data-extraction-rules.txt"
  local file_paths_policy="$TEMP_ROOT/$prefix-file-paths.txt"
  apk_commit="$(manifest_metadata_value "$apk" \
    'com.dailybeat.app.patrolgrid.RELEASE_COMMIT' "$TEMP_ROOT/$prefix-manifest.xml")"
  backend="$(manifest_metadata_value "$apk" \
    'com.dailybeat.app.patrolgrid.BACKEND_IDENTITY' "$TEMP_ROOT/$prefix-manifest.xml")"
  privacy_status="$(manifest_metadata_value "$apk" \
    'com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS' "$TEMP_ROOT/$prefix-manifest.xml")"
  privacy_notice_version="$(manifest_metadata_value "$apk" \
    'com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION' "$TEMP_ROOT/$prefix-manifest.xml")"
  [[ "$apk_commit" == "$commit" ]] || fail "$prefix APK commit metadata mismatch"
  [[ "$backend" == "$("$JQ" -r '.backend.identity' "$CANDIDATE_MANIFEST")" ]] ||
    fail "$prefix APK backend metadata mismatch"
  [[ "$privacy_status" == "$("$JQ" -r '.privacyPolicy.status' "$CANDIDATE_MANIFEST")" &&
      "$privacy_notice_version" == \
        "$("$JQ" -r '.privacyPolicy.noticeVersion' "$CANDIDATE_MANIFEST")" ]] ||
    fail "$prefix APK privacy approval/version metadata mismatch"
  "$AAPT" dump xmltree "$apk" res/xml/network_security_config.xml > "$network_policy" ||
    fail "$prefix APK network-security policy cannot be inspected"
  "$AAPT" dump xmltree "$apk" res/xml/data_extraction_rules.xml > "$extraction_policy" ||
    fail "$prefix APK data-extraction policy cannot be inspected"
  "$AAPT" dump xmltree "$apk" res/xml/file_paths.xml > "$file_paths_policy" ||
    fail "$prefix APK FileProvider paths policy cannot be inspected"
  "$AAPT" dump --values resources "$apk" > "$resources" ||
    fail "$prefix APK compiled resource table cannot be inspected"
  manifest_hash="$("$PYTHON" -I "$MANIFEST_VERIFIER" "$TEMP_ROOT/$prefix-manifest.xml" \
    "$resources" "$network_policy" "$extraction_policy" "$file_paths_policy" \
    "$EXPECTED_PACKAGE" "$version" "$version_code" "$commit" "$backend" \
    "$privacy_status" "$privacy_notice_version")" ||
    fail "$prefix APK merged manifest violates the exact release policy"
  [[ "$manifest_hash" == "$("$JQ" -r '.android.manifestSha256' "$CANDIDATE_MANIFEST")" ]] ||
    fail "$prefix APK normalized manifest changed from the sealed candidate"
  if [[ "$expected_signed" == 'no' ]]; then
    if "$APKSIGNER" verify --min-sdk-version 26 "$apk" >/dev/null 2>&1; then
      fail "unsigned candidate is unexpectedly signed"
    fi
    return
  fi
  "$ZIPALIGN" -c -P 16 4 "$apk" >/dev/null || fail "$prefix APK is not zipaligned"
  signer_output="$("$APKSIGNER" verify --min-sdk-version 26 --verbose --print-certs "$apk" 2>&1)" ||
    fail "$prefix APK signature verification failed"
  if ! /usr/bin/grep -qF 'Verified using v1 scheme (JAR signing): false' <<<"$signer_output" ||
      ! /usr/bin/grep -qF 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$signer_output" ||
      ! /usr/bin/grep -qF 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$signer_output" ||
      ! /usr/bin/grep -qF 'Verified using v4 scheme (APK Signature Scheme v4): false' <<<"$signer_output" ||
      ! /usr/bin/grep -qF 'Number of signers: 1' <<<"$signer_output"; then
    fail "$prefix APK does not have the exact v2/v3 single-signer signature policy"
  fi
  signer_digests="$(sed -n \
    -e 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' \
    -e 's/^V[0-9][0-9.]* Signer: certificate SHA-256 digest: //p' \
    <<<"$signer_output" | tr '[:upper:]' '[:lower:]' |
    sed 's/[^0-9a-f]//g; /^$/d' | sort -u)"
  [[ "$(sed '/^$/d' <<<"$signer_digests" | wc -l | tr -d ' ')" == '1' &&
      "$signer_digests" == "$APK_CERT_SHA256" ]] ||
    fail "$prefix APK signer is not the single pinned certificate"
}

sign_with_apk_key() {
  local input="$1"
  local output="$2"
  keychain_item_exists "$APK_SIGNING_SERVICE"
  {
    "$SECURITY" find-generic-password -w -s "$APK_SIGNING_SERVICE" -a "$KEYCHAIN_ACCOUNT"
    "$SECURITY" find-generic-password -w -s "$APK_SIGNING_SERVICE" -a "$KEYCHAIN_ACCOUNT"
  } | "$APKSIGNER" sign --min-sdk-version 26 \
    --ks "$RELEASE_KEYSTORE" --ks-type PKCS12 --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --ks-pass stdin --key-pass stdin --v1-signing-enabled false \
    --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --out "$output" "$input" >/dev/null || fail "offline APK signing failed"
  require_regular_private_file "$output"
}

perform_ceremony() {
  local tag="$1"
  local requested_bundle_dir="$2"
  local version=''
  local major=''
  local minor=''
  local patch=''
  local version_code=''
  local plaintext_archive=''
  local aligned_apk=''
  local signed_apk=''
  local final_dir=''
  local apk_name=''
  local checksum_name=''
  local sbom_name=''
  local metadata_json_name=''
  local metadata_asset_name='assets/patrolgrid-release.json'
  local metadata_asset_dir=''
  local with_metadata_apk=''
  local extracted_metadata=''

  if [[ ! "$tag" =~ ^patrolgrid-v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    fail "tag must be patrolgrid-vMAJOR.MINOR.PATCH"
  fi
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  (( minor <= 99 && patch <= 99 )) || fail "MINOR and PATCH must be at most 99"
  version="$major.$minor.$patch"
  version_code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
  (( version_code >= 1 && version_code <= 2100000000 )) || fail "invalid Android version code"
  prepare_new_path "$requested_bundle_dir" 'owner release bundle'
  preflight
  verify_monotonic_tag_version "$tag" "$version_code" 1
  resolve_authenticated_draft "$tag" "$version"
  plaintext_archive="$TEMP_ROOT/candidate.tar"
  decrypt_candidate "$TEMP_ROOT/$RESOLVED_ASSET_NAME" "$plaintext_archive"
  extract_exact_candidate "$plaintext_archive" "$version" "$RESOLVED_COMMIT"
  verify_manifest_and_provenance "$tag" "$version" "$version_code" "$RESOLVED_COMMIT"
  verify_apk_identity "$CANDIDATE_UNSIGNED" "$version" "$version_code" \
    "$RESOLVED_COMMIT" no unsigned

  apk_name="PatrolGrid-$version.apk"
  checksum_name="PatrolGrid-$version-SHA256SUMS.txt"
  sbom_name="PatrolGrid-$version.spdx.json"
  metadata_json_name="PatrolGrid-$version-release.json"
  PREPARED_RELEASE_DIR="$PREPARED_BUNDLE_DIR/staff"
  PREPARED_MAPPING_FILE="$PREPARED_BUNDLE_DIR/owner/PatrolGrid-$version-mapping.txt"
  final_dir="$TEMP_ROOT/final"
  "$INSTALL" -d -m 0700 "$final_dir"
  /bin/chmod -N "$final_dir" || fail "could not clear inherited final-output ACL"
  require_designated_private_directory "$final_dir" '700' 'private final-output directory'
  verified_private_copy "$CANDIDATE_SBOM" "$final_dir/$sbom_name" 'final SBOM copy'
  [[ "$(sha256_file "$final_dir/$sbom_name")" == \
      "$("$JQ" -r '.artifacts.sbom.sha256' "$CANDIDATE_MANIFEST")" ]] ||
    fail "final SBOM differs from the candidate-manifest SBOM"

  "$JQ" -nS --arg applicationId "$EXPECTED_PACKAGE" --arg versionName "$version" \
    --argjson versionCode "$version_code" --arg tag "$tag" --arg commit "$RESOLVED_COMMIT" \
    --arg repository "$REPOSITORY" \
    --arg backendIdentity "$("$JQ" -r '.backend.identity' "$CANDIDATE_MANIFEST")" \
    --arg backendKeyHash "$("$JQ" -r '.backend.supabaseAnonKeySha256' "$CANDIDATE_MANIFEST")" \
    --arg backendUrlHash "$("$JQ" -r '.backend.supabaseUrlSha256' "$CANDIDATE_MANIFEST")" \
    --arg privacyPolicyUrl "$("$JQ" -r '.privacyPolicy.url' "$CANDIDATE_MANIFEST")" \
    --arg privacyPolicyHash "$("$JQ" -r '.privacyPolicy.sha256' "$CANDIDATE_MANIFEST")" \
    --arg privacyPolicyStatus "$("$JQ" -r '.privacyPolicy.status' "$CANDIDATE_MANIFEST")" \
    --argjson privacyNoticeVersion \
      "$("$JQ" -r '.privacyPolicy.noticeVersion' "$CANDIDATE_MANIFEST")" \
    --arg candidateAsset "$RESOLVED_ASSET_NAME" --arg candidateDigest "$RESOLVED_ASSET_DIGEST" \
    --arg candidateManifestHash "$(sha256_file "$CANDIDATE_MANIFEST")" \
    --arg unsignedApkHash "$(sha256_file "$CANDIDATE_UNSIGNED")" \
    --argjson actionsArtifactId "$RESOLVED_ACTIONS_ARTIFACT_ID" \
    --arg actionsArtifactArchiveDigest "$RESOLVED_ACTIONS_ARTIFACT_DIGEST" \
    --argjson draftReleaseId "$RESOLVED_RELEASE_ID" \
    --argjson draftAssetId "$RESOLVED_RELEASE_ASSET_ID" \
    --arg sbomHash "$(sha256_file "$final_dir/$sbom_name")" \
    --arg mappingHash "$(sha256_file "$CANDIDATE_MAPPING")" \
    --arg androidManifestHash "$("$JQ" -r '.android.manifestSha256' "$CANDIDATE_MANIFEST")" \
    --arg workflowRef "$("$JQ" -r '.workflow.ref' "$CANDIDATE_MANIFEST")" \
    --argjson runId "$("$JQ" -r '.workflow.runId' "$CANDIDATE_MANIFEST")" \
    --argjson runAttempt "$("$JQ" -r '.workflow.runAttempt' "$CANDIDATE_MANIFEST")" \
    '{schemaVersion:1,applicationId:$applicationId,versionName:$versionName,
      versionCode:$versionCode,releaseTag:$tag,commit:$commit,repository:$repository,
      backend:{identity:$backendIdentity,supabaseAnonKeySha256:$backendKeyHash,
               supabaseUrlSha256:$backendUrlHash},
      privacyPolicy:{noticeVersion:$privacyNoticeVersion,sha256:$privacyPolicyHash,
                     status:$privacyPolicyStatus,url:$privacyPolicyUrl},
      workflow:{ref:$workflowRef,runId:$runId,runAttempt:$runAttempt},
      candidate:{actionsArtifactArchiveSha256:($actionsArtifactArchiveDigest|sub("^sha256:";"")),
                 actionsArtifactId:$actionsArtifactId,assetName:$candidateAsset,
                 ciphertextSha256:($candidateDigest|sub("^sha256:";"")),
                 draftAssetId:$draftAssetId,draftReleaseId:$draftReleaseId,
                 manifestSha256:$candidateManifestHash,unsignedApkSha256:$unsignedApkHash},
      artifacts:{androidManifestSha256:$androidManifestHash,
                 sbomSha256:$sbomHash,mappingSha256:$mappingHash}}' > "$TEMP_ROOT/$metadata_json_name"
  "$JQ" -e --arg commit "$RESOLVED_COMMIT" --arg tag "$tag" \
    'keys == ["applicationId","artifacts","backend","candidate","commit","privacyPolicy","releaseTag","repository","schemaVersion","versionCode","versionName","workflow"] and
     .schemaVersion == 1 and .commit == $commit and .releaseTag == $tag and
     .workflow.ref == (.repository+"/.github/workflows/release.yml@refs/tags/"+$tag) and
     .privacyPolicy.status == "APPROVED" and .privacyPolicy.noticeVersion == 3 and
     (.candidate|keys) == ["actionsArtifactArchiveSha256","actionsArtifactId","assetName","ciphertextSha256","draftAssetId","draftReleaseId","manifestSha256","unsignedApkSha256"] and
     (.candidate.assetName|test("^PatrolGrid-[0-9]+\\.[0-9]+\\.[0-9]+-[0-9a-f]{40}-unsigned-candidate\\.tar\\.gpg$")) and
     (.candidate.actionsArtifactId|type) == "number" and .candidate.actionsArtifactId > 0 and
     (.candidate.draftAssetId|type) == "number" and .candidate.draftAssetId > 0 and
     (.candidate.draftReleaseId|type) == "number" and .candidate.draftReleaseId > 0 and
     all(.candidate|to_entries[]|select(.key|endswith("Sha256"));
       (.value|type) == "string" and (.value|test("^[0-9a-f]{64}$"))) and
     (.artifacts|keys) == ["androidManifestSha256","mappingSha256","sbomSha256"] and
     all(.artifacts[]; test("^[0-9a-f]{64}$"))' \
    "$TEMP_ROOT/$metadata_json_name" >/dev/null || fail "final release metadata is invalid"

  metadata_asset_dir="$TEMP_ROOT/assets"
  "$INSTALL" -d -m 0700 "$metadata_asset_dir"
  "$INSTALL" -m 0600 "$TEMP_ROOT/$metadata_json_name" "$metadata_asset_dir/patrolgrid-release.json"
  with_metadata_apk="$TEMP_ROOT/with-release-metadata.apk"
  "$INSTALL" -m 0600 "$CANDIDATE_UNSIGNED" "$with_metadata_apk"
  (cd "$TEMP_ROOT" && "$ZIP" -X -q "$with_metadata_apk" "$metadata_asset_name")
  [[ "$("$UNZIP" -Z1 "$with_metadata_apk" | /usr/bin/grep -cx "$metadata_asset_name")" == '1' ]] ||
    fail "release metadata was not embedded exactly once"
  aligned_apk="$TEMP_ROOT/aligned.apk"
  signed_apk="$TEMP_ROOT/signed.apk"
  "$ZIPALIGN" -P 16 -f 4 "$with_metadata_apk" "$aligned_apk"
  reverify_governance_before_private_signing "$tag" "$version_code" "$RESOLVED_COMMIT"
  sign_with_apk_key "$aligned_apk" "$signed_apk"
  verify_apk_identity "$signed_apk" "$version" "$version_code" "$RESOLVED_COMMIT" yes signed
  extracted_metadata="$TEMP_ROOT/reverified-release.json"
  (umask 077; set -o noclobber; "$UNZIP" -p "$signed_apk" "$metadata_asset_name" > "$extracted_metadata") ||
    fail "could not extract APK-signed release metadata"
  /usr/bin/cmp -s "$TEMP_ROOT/$metadata_json_name" "$extracted_metadata" ||
    fail "APK-signed release metadata content changed"
  verified_private_copy "$signed_apk" "$final_dir/$apk_name" 'final signed APK copy'
  verify_apk_identity "$final_dir/$apk_name" "$version" "$version_code" \
    "$RESOLVED_COMMIT" yes final-copy

  (
    cd "$final_dir"
    "$SHASUM" -a 256 "$apk_name" "$sbom_name" > "$checksum_name"
    "$SHASUM" -a 256 -c "$checksum_name" >/dev/null
  )
  /bin/chmod 0600 "$final_dir/$checksum_name"
  /bin/chmod -N "$final_dir/$checksum_name" || fail "could not clear final checksum ACL"
  require_designated_private_file "$final_dir/$checksum_name" '600' 'final checksum file'
  [[ "$(find "$final_dir" -mindepth 1 -maxdepth 1 -type f | wc -l | tr -d ' ')" == '3' ]]
  [[ -z "$(find "$final_dir" -mindepth 1 -maxdepth 1 \( ! -type f -o -type l \) -print)" ]]

  STAGED_BUNDLE_DIR="$($MKTEMP -d "$(dirname "$PREPARED_BUNDLE_DIR")/.patrolgrid-bundle.XXXXXX")"
  [[ -d "$STAGED_BUNDLE_DIR" && ! -L "$STAGED_BUNDLE_DIR" ]] ||
    fail "could not create the same-parent owner-bundle stage"
  "$INSTALL" -d -m 0700 "$STAGED_BUNDLE_DIR/staff" "$STAGED_BUNDLE_DIR/owner"
  /bin/chmod 0700 "$STAGED_BUNDLE_DIR" "$STAGED_BUNDLE_DIR/staff" "$STAGED_BUNDLE_DIR/owner"
  /bin/chmod -N "$STAGED_BUNDLE_DIR" "$STAGED_BUNDLE_DIR/staff" "$STAGED_BUNDLE_DIR/owner" ||
    fail "could not clear inherited owner-bundle ACLs"
  require_designated_private_directory "$STAGED_BUNDLE_DIR" '700' 'owner-bundle stage'
  require_designated_private_directory "$STAGED_BUNDLE_DIR/staff" '700' 'staff subtree stage'
  require_designated_private_directory "$STAGED_BUNDLE_DIR/owner" '700' 'owner subtree stage'
  verified_private_copy "$CANDIDATE_MAPPING" \
    "$STAGED_BUNDLE_DIR/owner/$(basename "$PREPARED_MAPPING_FILE")" 'mapping stage'
  [[ "$(sha256_file "$STAGED_BUNDLE_DIR/owner/$(basename "$PREPARED_MAPPING_FILE")")" == \
      "$(sha256_file "$CANDIDATE_MAPPING")" ]] || fail "mapping stage changed during copy"
  for output_name in "$apk_name" "$checksum_name" "$sbom_name"; do
    verified_private_copy "$final_dir/$output_name" "$STAGED_BUNDLE_DIR/staff/$output_name" \
      'staff-stage file'
    [[ "$(sha256_file "$STAGED_BUNDLE_DIR/staff/$output_name")" == \
        "$(sha256_file "$final_dir/$output_name")" ]] || fail "staff-package stage changed during copy"
  done
  local publish_status=0
  if "$PYTHON" -I "$OUTPUT_PUBLISHER" "$STAGED_BUNDLE_DIR" "$PREPARED_BUNDLE_DIR"; then
    STAGED_BUNDLE_DIR=''
  else
    publish_status=$?
    if [[ "$publish_status" == '21' ]]; then
      STAGED_BUNDLE_DIR=''
      fail "the complete atomic owner bundle requires durability quarantine; do not distribute it"
    fi
    fail "atomic owner-bundle publication failed safely; no release bundle was published"
  fi
  require_designated_private_directory "$PREPARED_BUNDLE_DIR" '700' 'published owner bundle'
  require_designated_private_file "$PREPARED_MAPPING_FILE" '600' 'published mapping'
  require_designated_private_directory "$PREPARED_RELEASE_DIR" '700' 'published staff package'
  /usr/bin/cmp -s "$PREPARED_MAPPING_FILE" "$CANDIDATE_MAPPING" ||
    fail "published mapping differs from the candidate mapping"
  for output_name in "$apk_name" "$checksum_name" "$sbom_name"; do
    require_designated_private_file "$PREPARED_RELEASE_DIR/$output_name" '600' \
      'published staff-package file'
    [[ "$(sha256_file "$PREPARED_RELEASE_DIR/$output_name")" == \
        "$(sha256_file "$final_dir/$output_name")" ]] || fail "published staff-package file changed"
  done
  /usr/bin/cmp -s "$PREPARED_RELEASE_DIR/$apk_name" "$signed_apk" ||
    fail "published APK differs from the independently verified signed APK"
  /usr/bin/cmp -s "$PREPARED_RELEASE_DIR/$sbom_name" "$CANDIDATE_SBOM" ||
    fail "published SBOM differs from the candidate-manifest SBOM"
  (
    cd "$PREPARED_RELEASE_DIR"
    "$SHASUM" -a 256 -c "$checksum_name" >/dev/null
  ) || fail "published staff-package checksums do not verify"
  verify_apk_identity "$PREPARED_RELEASE_DIR/$apk_name" "$version" "$version_code" \
    "$RESOLVED_COMMIT" yes published
  echo "Verified signed PatrolGrid $version staff package: $PREPARED_RELEASE_DIR"
  echo "Owner-only R8 mapping (never send to staff): $PREPARED_MAPPING_FILE"
  echo "Full source commit: $RESOLVED_COMMIT"
}

case "${1:-}" in
  check)
    [[ "$#" == '1' ]] || usage
    require_trusted_entrypoint check
    preflight
    echo "Offline PatrolGrid release keys, certificate, SDK 36.0.0 tools, and GitHub authentication are ready."
    ;;
  create-tag)
    [[ "$#" == '2' ]] || usage
    require_trusted_entrypoint create-tag
    create_signed_release_tag "$2"
    ;;
  ceremony)
    [[ "$#" == '3' ]] || usage
    require_trusted_entrypoint ceremony
    perform_ceremony "$2" "$3"
    ;;
  *) usage ;;
esac
