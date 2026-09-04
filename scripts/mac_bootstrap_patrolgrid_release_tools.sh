#!/usr/bin/env -S -i PATROLGRID_CLEAN_ENV=1 HOME=/Users/sujithsampath PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C /bin/bash --noprofile --norc
# shellcheck shell=bash
# One-time, human-audited bootstrap for the root-sealed PatrolGrid release launcher.
set -euo pipefail
[[ "${PATROLGRID_CLEAN_ENV:-}" == '1' ]] || {
  echo 'Run this bootstrap only through its clean-environment executable entrypoint.' >&2
  exit 1
}
export PATH='/usr/bin:/bin:/usr/sbin:/sbin' LANG='C' LC_ALL='C'
unset BASH_ENV ENV CDPATH GLOBIGNORE GH_TOKEN GITHUB_TOKEN GH_HOST GNUPGHOME GPG_AGENT_INFO \
  JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS JAVA_OPTS CLASSPATH PYTHONHOME PYTHONPATH \
  DYLD_INSERT_LIBRARIES DYLD_LIBRARY_PATH TAR_OPTIONS ZIPOPT UNZIP UNZIPOPT \
  SSL_CERT_FILE SSL_CERT_DIR GIT_SSL_NO_VERIFY http_proxy https_proxy all_proxy no_proxy \
  HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE \
  GIT_CONFIG GIT_CONFIG_GLOBAL GIT_CONFIG_SYSTEM GIT_CONFIG_COUNT GIT_EXEC_PATH GIT_SSH_COMMAND \
  GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_COMMON_DIR GIT_SHALLOW_FILE \
  GIT_REPLACE_REF_BASE
export GIT_NO_REPLACE_OBJECTS='1'

readonly SOURCE_ROOT='/Users/sujithsampath/Documents/Codex/2026-09-01/ac/dailybeat'
readonly INSTALL_ROOT='/Library/Application Support/PatrolGrid/release-tools/current'
readonly REPOSITORY='sampathmannam/dailybeat'
readonly GH='/opt/homebrew/Cellar/gh/2.95.0/bin/gh'
readonly EXPECTED_GH_SHA256='798882434e7f6ae5846194191263ecc59d56bc201f13f016270f44cb4f34499e'
readonly GIT='/usr/bin/git'
readonly SHASUM='/usr/bin/shasum'
readonly MKTEMP='/usr/bin/mktemp'
readonly SUDO='/usr/bin/sudo'
readonly INSTALL='/usr/bin/install'
readonly STAT='/usr/bin/stat'
readonly ID='/usr/bin/id'
readonly PUBLIC_KEY_SHA256='0711013ee3dafc2d9245a32cd2a94b94af16ce8c28fd262f267e8c5127861b55'
readonly BOOTSTRAP_RELATIVE='scripts/mac_bootstrap_patrolgrid_release_tools.sh'
readonly BOOTSTRAP_PATH="$SOURCE_ROOT/$BOOTSTRAP_RELATIVE"
STAGE=''

stop() { echo "PatrolGrid trusted-tool bootstrap stopped: $*" >&2; exit 1; }
git_read() { GIT_NO_REPLACE_OBJECTS=1 "$GIT" --no-replace-objects "$@"; }
require_no_extended_acl() {
  local listing=''
  listing="$(/bin/ls -lde "$1")" || stop "cannot inspect path ACL: $1"
  [[ "$(/usr/bin/printf '%s\n' "$listing" | /usr/bin/wc -l | /usr/bin/tr -d ' ')" == '1' ]] ||
    stop "path has an extended ACL: $1"
}
require_stage_path() {
  local path="$1"
  local expected_mode="$2"
  local expected_type="$3"
  [[ "$($STAT -f '%Su' "$path")" == 'sujithsampath' &&
      "$($STAT -f '%Lp' "$path")" == "$expected_mode" &&
      "$($STAT -f '%HT' "$path")" == "$expected_type" && ! -L "$path" ]] ||
    stop "bootstrap staging path has unsafe ownership, mode, or type: $path"
  require_no_extended_acl "$path"
}
require_root_path() {
  local path="$1"
  local expected_mode="$2"
  local expected_type="$3"
  [[ "$($STAT -f '%Su:%Sg:%Lp:%HT' "$path")" == \
      "root:wheel:$expected_mode:$expected_type" && ! -L "$path" ]] ||
    stop "installed trust-boundary path has unsafe ownership, mode, or type: $path"
  require_no_extended_acl "$path"
}
cleanup() {
  if [[ -n "${STAGE:-}" && -d "$STAGE" && ! -L "$STAGE" && "$STAGE" == /tmp/.patrolgrid-bootstrap.* ]]; then
    /bin/chmod -R u+rwX -- "$STAGE" 2>/dev/null || true
    /bin/rm -rf -- "$STAGE"
  fi
}
trap cleanup EXIT

[[ "$#" == 2 && "$1" == 'install' && "$2" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Usage: ./scripts/mac_bootstrap_patrolgrid_release_tools.sh install <reviewed-current-main-commit>" >&2
  exit 2
}
readonly REVIEWED_COMMIT="$2"
[[ -x "$GH" && -x "$GIT" && -x "$SHASUM" && -x "$SUDO" && -x "$INSTALL" ]] ||
  stop 'a fixed bootstrap tool is missing'
[[ "$($ID -un)" == 'sujithsampath' ]] ||
  stop 'bootstrap must run as the designated PatrolGrid release account'
[[ "$($SHASUM -a 256 "$GH" | /usr/bin/awk '{print tolower($1)}')" == "$EXPECTED_GH_SHA256" ]] ||
  stop 'fixed GitHub CLI digest changed'
executing_bootstrap="$(cd "$(dirname "$0")" && pwd -P)/$(basename "$0")"
[[ "$executing_bootstrap" == "$BOOTSTRAP_PATH" && -f "$BOOTSTRAP_PATH" &&
    ! -L "$BOOTSTRAP_PATH" ]] || stop 'run only the canonical non-symlink bootstrap'
git_directory="$(git_read -C "$SOURCE_ROOT" rev-parse --absolute-git-dir)"
[[ "$git_directory" == "$SOURCE_ROOT/.git" && -d "$git_directory" && ! -L "$git_directory" ]] ||
  stop 'canonical source must use its standard non-symlink Git directory'
[[ -z "$(git_read -C "$SOURCE_ROOT" for-each-ref --format='%(refname)' refs/replace)" ]] ||
  stop 'Git replacement refs are forbidden in the canonical source'
[[ ! -e "$git_directory/info/grafts" && ! -L "$git_directory/info/grafts" ]] ||
  stop 'Git grafts are forbidden in the canonical source'
[[ "$(git_read -C "$SOURCE_ROOT" rev-parse --show-toplevel)" == "$SOURCE_ROOT" &&
    "$(git_read -C "$SOURCE_ROOT" symbolic-ref --short HEAD)" == 'main' &&
    "$(git_read -C "$SOURCE_ROOT" rev-parse HEAD)" == "$REVIEWED_COMMIT" ]] ||
  stop 'canonical source must be the reviewed local main commit'
[[ -z "$(git_read -C "$SOURCE_ROOT" status --porcelain=v1 --untracked-files=all)" ]] ||
  stop 'canonical source must be completely clean before bootstrap'
/usr/bin/cmp -s "$BOOTSTRAP_PATH" \
  <(git_read -C "$SOURCE_ROOT" cat-file blob "$REVIEWED_COMMIT:$BOOTSTRAP_RELATIVE") ||
  stop 'executing bootstrap differs from the reviewed commit'
remote_main="$($GH api --hostname github.com -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2022-11-28' "/repos/$REPOSITORY/git/ref/heads/main" --jq '.object.sha')"
[[ "$remote_main" == "$REVIEWED_COMMIT" ]] || stop 'reviewed commit is not current GitHub main'
[[ ! -e "$INSTALL_ROOT" && ! -L "$INSTALL_ROOT" ]] ||
  stop "trusted installation exists; no automated replacement is shipped—record a new independent audit and admin reinstall"

umask 077
STAGE="$($MKTEMP -d '/tmp/.patrolgrid-bootstrap.XXXXXX')"
[[ -d "$STAGE" && ! -L "$STAGE" ]] || stop 'could not create private staging directory'
for directory in bin scripts release; do "$INSTALL" -d -m 0700 "$STAGE/$directory"; done
/bin/chmod 0700 "$STAGE" "$STAGE/bin" "$STAGE/scripts" "$STAGE/release"
/bin/chmod -N "$STAGE" "$STAGE/bin" "$STAGE/scripts" "$STAGE/release" ||
  stop 'could not clear inherited ACLs from bootstrap staging directories'
for directory in "$STAGE" "$STAGE/bin" "$STAGE/scripts" "$STAGE/release"; do
  require_stage_path "$directory" '700' 'Directory'
done
declare -a FILES=(
  'scripts/mac_patrolgrid_release_launcher.py:bin/patrolgrid-release:0555'
  'scripts/mac_decrypt_patrolgrid_release.sh:scripts/mac_decrypt_patrolgrid_release.sh:0555'
  'scripts/mac_install_release_apk.sh:scripts/mac_install_release_apk.sh:0555'
  'scripts/mac_adb_common.sh:scripts/mac_adb_common.sh:0444'
  'scripts/verify_patrolgrid_release_manifest.py:scripts/verify_patrolgrid_release_manifest.py:0555'
  'scripts/patrolgrid_apkanalyzer.py:scripts/patrolgrid_apkanalyzer.py:0555'
  'scripts/patrolgrid_publish_release.py:scripts/patrolgrid_publish_release.py:0555'
  'scripts/verify_patrolgrid_openpgp_packets.py:scripts/verify_patrolgrid_openpgp_packets.py:0555'
  'release/patrolgrid-release-public-key.asc:release/patrolgrid-release-public-key.asc:0444'
  'release/patrolgrid-release-cert.pem:release/patrolgrid-release-cert.pem:0444'
  'release/patrolgrid-apkanalyzer-classpath.sha256:release/patrolgrid-apkanalyzer-classpath.sha256:0444'
)
for specification in "${FILES[@]}"; do
  IFS=: read -r source destination mode <<<"$specification"
  [[ -f "$SOURCE_ROOT/$source" && ! -L "$SOURCE_ROOT/$source" ]] || stop "unsafe source: $source"
  git_read -C "$SOURCE_ROOT" cat-file blob "$REVIEWED_COMMIT:$source" > "$STAGE/$destination"
  /bin/chmod "$mode" "$STAGE/$destination"
  /bin/chmod -N "$STAGE/$destination" || stop "could not clear bootstrap-stage ACL: $destination"
  require_stage_path "$STAGE/$destination" "${mode#0}" 'Regular File'
  /usr/bin/cmp -s "$SOURCE_ROOT/$source" "$STAGE/$destination" ||
    stop "working copy differs from reviewed commit: $source"
done
[[ "$($SHASUM -a 256 "$STAGE/release/patrolgrid-release-public-key.asc" | /usr/bin/awk '{print tolower($1)}')" == \
    "$PUBLIC_KEY_SHA256" ]] || stop 'reviewed public release key digest is unexpected'
/usr/bin/printf '%s\n' "$REVIEWED_COMMIT" > "$STAGE/REVIEWED_COMMIT"
/bin/chmod 0444 "$STAGE/REVIEWED_COMMIT"
/bin/chmod -N "$STAGE/REVIEWED_COMMIT" || stop 'could not clear reviewed-commit stage ACL'
require_stage_path "$STAGE/REVIEWED_COMMIT" '444' 'Regular File'

echo 'About to install an audited, root-owned PatrolGrid trust boundary.'
echo 'This is intentionally one-time. Review this bootstrap and the staged source commit before approving sudo.'
"$SUDO" "$INSTALL" -d -o root -g wheel -m 0755 \
  '/Library/Application Support/PatrolGrid' \
  '/Library/Application Support/PatrolGrid/release-tools' "$INSTALL_ROOT"
for directory in bin scripts release; do
  "$SUDO" "$INSTALL" -d -o root -g wheel -m 0755 "$INSTALL_ROOT/$directory"
done
for specification in "${FILES[@]}"; do
  IFS=: read -r _ destination mode <<<"$specification"
  "$SUDO" "$INSTALL" -o root -g wheel -m "$mode" "$STAGE/$destination" "$INSTALL_ROOT/$destination"
  /usr/bin/cmp -s "$STAGE/$destination" "$INSTALL_ROOT/$destination" ||
    stop "root-installed file verification failed: $destination"
done
"$SUDO" "$INSTALL" -o root -g wheel -m 0444 "$STAGE/REVIEWED_COMMIT" "$INSTALL_ROOT/REVIEWED_COMMIT"
ACL_PATHS=(
  '/Library/Application Support/PatrolGrid'
  '/Library/Application Support/PatrolGrid/release-tools'
  "$INSTALL_ROOT" "$INSTALL_ROOT/bin" "$INSTALL_ROOT/scripts" "$INSTALL_ROOT/release"
  "$INSTALL_ROOT/REVIEWED_COMMIT"
)
for specification in "${FILES[@]}"; do
  IFS=: read -r _ destination _ <<<"$specification"
  ACL_PATHS+=("$INSTALL_ROOT/$destination")
done
"$SUDO" /bin/chmod -N "${ACL_PATHS[@]}" ||
  stop 'could not remove inherited ACLs from the root-owned trust boundary'
for directory in \
  '/Library/Application Support/PatrolGrid' \
  '/Library/Application Support/PatrolGrid/release-tools' \
  "$INSTALL_ROOT" "$INSTALL_ROOT/bin" "$INSTALL_ROOT/scripts" "$INSTALL_ROOT/release"; do
  require_root_path "$directory" '755' 'Directory'
done
for specification in "${FILES[@]}"; do
  IFS=: read -r _ destination mode <<<"$specification"
  require_root_path "$INSTALL_ROOT/$destination" "${mode#0}" 'Regular File'
done
require_root_path "$INSTALL_ROOT/REVIEWED_COMMIT" '444' 'Regular File'
echo "Installed the reviewed trust boundary at $INSTALL_ROOT."
echo "Next: make and verify two encrypted offline copies, then run $INSTALL_ROOT/bin/patrolgrid-release check"
