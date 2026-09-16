"""Exercise the installer with synthetic assets and fake Android tools; never touch a phone."""

import os
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[2]
CERTIFICATE = "44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f"


@pytest.fixture
def installer(tmp_path):
    project = tmp_path / "project"
    (project / "scripts").mkdir(parents=True)
    (project / "release").mkdir()
    for name in ("mac_install_release_apk.sh", "mac_adb_common.sh"):
        shutil.copy2(ROOT / "scripts" / name, project / "scripts" / name)
    (project / "release/version.txt").write_text("5.2.1\n")
    mock_bin = tmp_path / "bin"
    mock_bin.mkdir()
    trace = tmp_path / "trace.txt"
    trace.write_text("")
    scripts = {
        "java": "exit 0\n",
        "adb": '''printf 'adb %s\\n' "$*" >> "$QA_TRACE"
if [ "$1" = devices ]; then printf 'List of devices attached\\nSERIAL1\\tdevice\\n'; fi
''',
        "curl": '''output=""; url=""
while [ "$#" -gt 0 ]; do
  case "$1" in -o) output="$2"; shift;; https://*) url="$1";; esac
  shift
done
printf 'curl %s\\n' "$url" >> "$QA_TRACE"
if [[ "$url" = */SHA256SUMS.txt ]]; then
  checksum="$(printf synthetic-apk | shasum -a 256 | awk '{print $1}')"
  asset="DailyBeat-${DAILYBEAT_RELEASE_TAG:-v5.2.1}.apk"
  if [ "${QA_BAD_CHECKSUM:-0}" = 1 ]; then checksum="0000000000000000000000000000000000000000000000000000000000000000"; fi
  if [ "${QA_MISSING_CHECKSUM:-0}" = 1 ]; then asset="unrelated.apk"; fi
  printf '%s  %s\\n' "$checksum" "$asset" > "$output"
  if [ "${QA_DUPLICATE_CHECKSUM:-0}" = 1 ]; then printf '%s  %s\\n' "$checksum" "$asset" >> "$output"; fi
else
  printf synthetic-apk > "$output"
fi
''',
        "apksigner": f'''printf 'apksigner %s\\n' "$*" >> "$QA_TRACE"
printf 'Signer #1 certificate SHA-256 digest: %s\\n' "${{QA_CERTIFICATE:-{CERTIFICATE}}}"
''',
        "aapt": '''printf 'aapt %s\\n' "$*" >> "$QA_TRACE"
printf "package: name='%s' versionCode='28' versionName='%s'\\n" "${QA_PACKAGE:-com.dailybeat.app}" "${QA_VERSION:-5.2.1}"
''',
    }
    for name, body in scripts.items():
        command = mock_bin / name
        command.write_text("#!/usr/bin/env bash\nset -euo pipefail\n" + body)
        command.chmod(0o755)

    def run(**overrides):
        env = {key: value for key, value in os.environ.items()
               if not key.startswith(("DAILYBEAT_", "QA_"))}
        env.update(PATH=f"{mock_bin}:{env['PATH']}", TMPDIR=str(tmp_path), QA_TRACE=str(trace))
        env.update(overrides)
        result = subprocess.run(
            ["bash", str(project / "scripts/mac_install_release_apk.sh"), "SERIAL1"],
            env=env, text=True, capture_output=True, timeout=10,
        )
        return result, trace.read_text()

    return run


def test_installer_uses_version_marker_and_verifies_before_install(installer):
    result, trace = installer()
    assert result.returncode == 0, result.stdout + result.stderr
    assert "releases/download/v5.2.1/DailyBeat-v5.2.1.apk" in trace
    assert trace.index("apksigner verify --print-certs") < trace.index("adb -s SERIAL1 install -r")
    assert trace.index("aapt dump badging") < trace.index("adb -s SERIAL1 install -r")
    assert "uninstall" not in trace and "pm grant" not in trace and "install -r -d" not in trace


@pytest.mark.parametrize("override", [
    {"QA_BAD_CHECKSUM": "1"},
    {"QA_MISSING_CHECKSUM": "1"},
    {"QA_DUPLICATE_CHECKSUM": "1"},
    {"QA_CERTIFICATE": "0" * 64},
    {"QA_PACKAGE": "com.dailybeat.app.qa"},
    {"QA_VERSION": "5.2.0"},
    {"DAILYBEAT_RELEASE_TAG": "../../unexpected"},
])
def test_unverified_or_wrong_assets_never_reach_install(installer, override):
    result, trace = installer(**override)
    assert result.returncode != 0
    assert " install " not in trace


def test_explicit_stable_tag_is_supported_but_still_verified(installer):
    result, trace = installer(DAILYBEAT_RELEASE_TAG="v5.2.2", QA_VERSION="5.2.2")
    assert result.returncode == 0, result.stdout + result.stderr
    assert "releases/download/v5.2.2/DailyBeat-v5.2.2.apk" in trace
    assert "apksigner verify --print-certs" in trace
