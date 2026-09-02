from __future__ import annotations

import hashlib
import importlib.util
import os
import shlex
import stat
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "scripts/mac_install_release_apk.sh"
INSTALLER_SOURCE = INSTALLER.read_text(encoding="utf-8")
COMMON = (ROOT / "scripts/mac_adb_common.sh").read_text(encoding="utf-8")
QA_RUNNER = (ROOT / "scripts/mac_sync_and_run.sh").read_text(encoding="utf-8")
EMULATOR_RUNNER = (ROOT / "scripts/mac_emulator_demo.sh").read_text(encoding="utf-8")
SIGNER = "1b1351160170796ec9818047e790a5474c8544ad867f62736e8d93fe2a8c025b"
BOOTSTRAP = ROOT / "scripts/mac_bootstrap_patrolgrid_release_tools.sh"
BOOTSTRAP_SOURCE = BOOTSTRAP.read_text(encoding="utf-8")


def _run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(INSTALLER), *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def test_release_installer_refuses_mutable_checkout_even_before_usage():
    result = _run()
    assert result.returncode == 1
    assert "audited root-installed launcher" in result.stderr


def _run_audited_test_copy(tmp_path: Path, *args: str) -> subprocess.CompletedProcess[str]:
    """Exercise early installer validation without weakening the production script."""
    trusted = tmp_path / "trusted-release-tools"
    (trusted / "bin").mkdir(parents=True)
    (trusted / "scripts").mkdir()
    launcher = trusted / "bin/patrolgrid-release"
    launcher.write_text("test fixture\n", encoding="utf-8")
    source = INSTALLER_SOURCE.replace(
        "/Library/Application Support/PatrolGrid/release-tools/current", str(trusted)
    )
    installer = trusted / "scripts/mac_install_release_apk.sh"
    installer.write_text(source, encoding="utf-8")
    (trusted / "scripts/mac_adb_common.sh").write_text(COMMON, encoding="utf-8")
    verifier = ROOT / "scripts/verify_patrolgrid_release_manifest.py"
    (trusted / "scripts/verify_patrolgrid_release_manifest.py").write_bytes(verifier.read_bytes())
    for executable in (installer, trusted / "scripts/verify_patrolgrid_release_manifest.py"):
        executable.chmod(0o700)
    environment = {
        "HOME": "/Users/sujithsampath",
        "LANG": "C",
        "LC_ALL": "C",
        "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
        "PATROLGRID_CLEAN_ENV": "1",
        "PATROLGRID_TRUSTED_LAUNCHER": str(launcher),
    }
    return subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", str(installer), *args],
        text=True,
        capture_output=True,
        check=False,
        env=environment,
    )


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac release helper")
def test_audited_installer_usage_path_is_reachable(tmp_path: Path):
    result = _run_audited_test_copy(tmp_path)
    assert result.returncode == 2
    assert "verified-staff-directory" in result.stderr


def test_installer_has_no_network_ciphertext_or_raw_apk_interface():
    for forbidden in (
        "curl",
        "gh release",
        "PATROLGRID_RELEASE_TAG",
        "PATROLGRID_RELEASE_REPOSITORY",
        "decrypt",
    ):
        assert forbidden not in INSTALLER_SOURCE
    assert "verified-staff-directory" in INSTALLER_SOURCE
    assert "exactly three files" in INSTALLER_SOURCE


def test_installer_pins_sdk_36_tools_adb_and_android_studio_jbr():
    assert "SDK_ROOT='/Users/sujithsampath/Library/Android/sdk'" in INSTALLER_SOURCE
    assert 'BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"' in INSTALLER_SOURCE
    assert 'ADB_FIXED="$SDK_ROOT/platform-tools/adb"' in INSTALLER_SOURCE
    assert 'MAC_ADB_BINARY="$ADB_FIXED"' in INSTALLER_SOURCE
    assert "/Applications/Android Studio.app/Contents/jbr/Contents/Home" in INSTALLER_SOURCE
    assert "export PATH='/usr/bin:/bin:/usr/sbin:/sbin'" in INSTALLER_SOURCE
    assert 'MAC_ADB_BINARY="${MAC_ADB_BINARY:-adb}"' in COMMON
    assert 'mac_adb_client -s "$MAC_ADB_SERIAL"' in COMMON
    assert '"$MAC_ADB_BINARY" -P "$MAC_ADB_SERVER_PORT"' in COMMON
    assert "/Applications/Android Studio.app/Contents/jbr/Contents/Home" in COMMON


def test_installer_requires_exact_private_three_file_layout():
    for value in (
        '"$APK_NAME" "$CHECKSUM_NAME" "$SBOM_NAME"',
        "assets/patrolgrid-release.json",
        "staff package must contain exactly three files",
        "mode 0700",
        "mode 0600",
        "exact APK/checksum/SBOM layout",
        "checksum file does not have the exact APK/SBOM allowlist",
    ):
        assert value in INSTALLER_SOURCE
    assert "mapping.txt" not in INSTALLER_SOURCE


def test_installer_verifies_apk_before_trusting_embedded_metadata_and_adb():
    apk_verify = INSTALLER_SOURCE.index('signer_digest "$TMP_ROOT/$APK_NAME"')
    metadata_extract = INSTALLER_SOURCE.index('"$METADATA_ASSET_NAME" >')
    adb_start = INSTALLER_SOURCE.index('nodaemon server')
    assert apk_verify < metadata_extract < adb_start
    assert SIGNER in INSTALLER_SOURCE
    assert "APK-signed release metadata is absent or duplicated" in INSTALLER_SOURCE
    assert "signed release metadata schema is invalid" in INSTALLER_SOURCE
    assert "SBOM hash does not match metadata" in INSTALLER_SOURCE


def test_installer_binds_full_commit_backend_and_provenance_to_apk():
    for value in (
        '(.commit|test("^[0-9a-f]{40}$"))',
        "candidate.assetName",
        "ciphertextSha256",
        "manifestSha256",
        "unsignedApkSha256",
        "mappingSha256",
        "runAttempt",
        "runId",
        "RELEASE_COMMIT",
        "BACKEND_IDENTITY",
        "APK full commit does not match signed metadata",
        "APK backend identity does not match signed metadata",
    ):
        assert value in INSTALLER_SOURCE
    assert "UNCONFIGURED" in INSTALLER_SOURCE


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac release helper")
def test_installer_rejects_symlink_before_tools_or_adb(tmp_path: Path):
    release = tmp_path / "release"
    release.mkdir(mode=0o700)
    (release / "unexpected").write_text("x", encoding="utf-8")
    link = tmp_path / "release-link"
    link.symlink_to(release, target_is_directory=True)
    linked = _run_audited_test_copy(tmp_path / "fixture", str(link))
    assert linked.returncode != 0
    assert "symlinked" in linked.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac release helper")
def test_installer_rejects_non_private_directory_before_tools_or_adb(tmp_path: Path):
    release = tmp_path / "release"
    release.mkdir(mode=0o755)
    result = _run_audited_test_copy(tmp_path / "fixture", str(release))
    assert result.returncode != 0
    assert "mode 0700" in result.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="real macOS ACL semantics")
def test_installer_rejects_staff_directory_with_real_extended_acl(tmp_path: Path):
    release = tmp_path / "release"
    release.mkdir(mode=0o700)
    subprocess.run(["/bin/chmod", "+a", "everyone allow read", release], check=True)
    result = _run_audited_test_copy(tmp_path / "fixture", str(release))
    assert result.returncode != 0
    assert "extended ACL" in result.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="real macOS ACL semantics")
def test_shared_acl_guard_rejects_a_real_acl_on_a_private_file(tmp_path: Path):
    private_file = tmp_path / "private"
    private_file.write_text("secret\n", encoding="utf-8")
    private_file.chmod(0o600)
    subprocess.run(["/bin/chmod", "+a", "everyone allow read", private_file], check=True)
    function_body = INSTALLER_SOURCE.split("require_no_extended_acl() {", 1)[1].split("\n}\n", 1)[0]
    program = (
        "fail() { echo \"$*\" >&2; return 1; }\n"
        "require_no_extended_acl() {" + function_body + "\n}\n"
        "require_no_extended_acl \"$1\"\n"
    )
    result = subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", "-c", program, "acl-test", private_file],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode != 0
    assert "extended ACL" in result.stderr


def test_scripts_are_executable_and_qa_production_packages_remain_isolated():
    for script in (
        INSTALLER,
        ROOT / "scripts/mac_decrypt_patrolgrid_release.sh",
        ROOT / "scripts/mac_bootstrap_patrolgrid_release_tools.sh",
        ROOT / "scripts/mac_patrolgrid_release_launcher.py",
        ROOT / "scripts/verify_patrolgrid_release_manifest.py",
        ROOT / "scripts/mac_sync_and_run.sh",
        ROOT / "scripts/mac_emulator_demo.sh",
    ):
        assert script.stat().st_mode & stat.S_IXUSR
    assert "com.dailybeat.app.patrolgrid" in INSTALLER_SOURCE
    assert "com.dailybeat.app.patrolgrid" in QA_RUNNER
    assert '${BASE_PACKAGE}.qa' in QA_RUNNER
    assert "git checkout" not in QA_RUNNER
    assert "RECORD_AUDIO" not in QA_RUNNER
    assert "READ_CALL_LOG" not in QA_RUNNER
    assert "Refusing non-emulator adb target" in EMULATOR_RUNNER


@pytest.mark.parametrize("serial", ["bad serial", "$(touch nope)", "../phone"])
@pytest.mark.skipif(sys.platform != "darwin", reason="Mac release helper")
def test_installer_rejects_unsafe_adb_serial_before_adb(tmp_path: Path, serial: str):
    release = tmp_path / "release"
    release.mkdir(mode=0o700)
    result = _run_audited_test_copy(tmp_path / "fixture", str(release), serial)
    assert result.returncode != 0
    assert "unsupported characters" in result.stderr


def test_clean_shebang_blocks_hostile_bash_env_before_first_line(tmp_path: Path):
    marker = tmp_path / "bash-env-ran"
    hostile = tmp_path / "hostile.sh"
    hostile.write_text(f"/usr/bin/touch '{marker}'\n", encoding="utf-8")
    result = subprocess.run(
        [str(INSTALLER)],
        env={**os.environ, "BASH_ENV": str(hostile)},
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode != 0
    assert not marker.exists()
    assert "audited root-installed launcher" in result.stderr


def test_launcher_propagates_trusted_child_marker(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    launcher_path = ROOT / "scripts/mac_patrolgrid_release_launcher.py"
    specification = importlib.util.spec_from_file_location("patrolgrid_launcher_fixture", launcher_path)
    assert specification and specification.loader
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    observed = tmp_path / "observed-marker"
    child = tmp_path / "child.sh"
    child.write_text(
        "#!/bin/sh\nprintf '%s|%s' \"$PATROLGRID_TRUSTED_LAUNCHER\" \"$*\" > "
        + shlex.quote(str(observed)) + "\n",
        encoding="utf-8",
    )
    child.chmod(0o700)
    monkeypatch.setattr(module, "verify_trusted_installation", lambda: None)
    monkeypatch.setattr(module, "TRUSTED_HELPER", child)
    monkeypatch.setattr(module, "TRUSTED_INSTALLER", child)
    cases = (
        ([str(module.SELF), "check"], "check"),
        ([str(module.SELF), "create-tag", "patrolgrid-v1.0.0"],
         "create-tag patrolgrid-v1.0.0"),
        ([str(module.SELF), "install", "/tmp/staff"], "/tmp/staff"),
    )
    for argv, expected_arguments in cases:
        monkeypatch.setattr(sys, "argv", argv)
        assert module.main() == 0
        assert observed.read_text(encoding="utf-8") == f"{module.SELF}|{expected_arguments}"


def test_launcher_uses_one_fixed_clean_bash_boundary_for_every_subcommand():
    launcher = (ROOT / "scripts/mac_patrolgrid_release_launcher.py").read_text(encoding="utf-8")
    assert '["/bin/bash", "--noprofile", "--norc", str(script), *arguments]' in launcher
    assert "env=clean_environment(trusted_child=True)" in launcher
    for invocation in (
        "run_trusted_script(helper, arguments)",
        "run_trusted_script(TRUSTED_HELPER, arguments)",
        "run_trusted_script(TRUSTED_INSTALLER, arguments[1:])",
    ):
        assert invocation in launcher


def test_bootstrap_and_root_launcher_seal_every_executed_release_file():
    bootstrap = (ROOT / "scripts/mac_bootstrap_patrolgrid_release_tools.sh").read_text()
    launcher = (ROOT / "scripts/mac_patrolgrid_release_launcher.py").read_text()
    for relative in (
        "mac_decrypt_patrolgrid_release.sh",
        "mac_install_release_apk.sh",
        "mac_adb_common.sh",
        "verify_patrolgrid_release_manifest.py",
        "patrolgrid_publish_release.py",
        "verify_patrolgrid_openpgp_packets.py",
        "patrolgrid-release-public-key.asc",
        "patrolgrid-release-cert.pem",
    ):
        assert relative in bootstrap
        assert relative in launcher
    assert "chmod -N" in bootstrap
    assert "require_no_extended_acl" in launcher
    assert "release-account-owned mode 0700" in INSTALLER_SOURCE
    assert "release-account-owned mode 0600" in INSTALLER_SOURCE


def _git(repository: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["/usr/bin/git", "-C", str(repository), *arguments],
        text=True,
        capture_output=True,
        check=True,
    )


def _bootstrap_fixture_repository(tmp_path: Path) -> tuple[Path, Path, str]:
    repository = tmp_path / "canonical"
    repository.mkdir()
    _git(repository, "init", "--initial-branch=main", "--quiet")
    _git(repository, "config", "user.name", "PatrolGrid Test")
    _git(repository, "config", "user.email", "patrolgrid-test@example.invalid")
    fake_gh = tmp_path / "fixed-gh"
    fake_gh.write_text("#!/bin/sh\nexit 97\n", encoding="utf-8")
    fake_gh.chmod(0o700)
    fake_gh_digest = hashlib.sha256(fake_gh.read_bytes()).hexdigest()
    script = repository / "scripts/mac_bootstrap_patrolgrid_release_tools.sh"
    script.parent.mkdir()
    source = BOOTSTRAP_SOURCE.replace(
        "readonly SOURCE_ROOT='/Users/sujithsampath/Documents/Codex/2026-09-01/ac/dailybeat'",
        f"readonly SOURCE_ROOT='{repository}'",
    ).replace(
        "readonly INSTALL_ROOT='/Library/Application Support/PatrolGrid/release-tools/current'",
        f"readonly INSTALL_ROOT='{tmp_path / 'installed'}'",
    ).replace(
        "readonly GH='/opt/homebrew/Cellar/gh/2.95.0/bin/gh'",
        f"readonly GH='{fake_gh}'",
    ).replace(
        "readonly EXPECTED_GH_SHA256='798882434e7f6ae5846194191263ecc59d56bc201f13f016270f44cb4f34499e'",
        f"readonly EXPECTED_GH_SHA256='{fake_gh_digest}'",
    )
    script.write_text(source, encoding="utf-8")
    script.chmod(0o700)
    _git(repository, "add", "scripts/mac_bootstrap_patrolgrid_release_tools.sh")
    _git(repository, "commit", "--quiet", "-m", "reviewed bootstrap")
    commit = _git(repository, "rev-parse", "HEAD").stdout.strip()
    return repository, script, commit


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac bootstrap trust boundary")
def test_bootstrap_rejects_an_actual_commit_replacement_attack(tmp_path: Path):
    repository, script, reviewed = _bootstrap_fixture_repository(tmp_path)
    script.write_text(script.read_text(encoding="utf-8") + "\n# attacker-controlled replacement tree\n")
    _git(repository, "add", "scripts/mac_bootstrap_patrolgrid_release_tools.sh")
    _git(repository, "commit", "--quiet", "-m", "replacement tree")
    replacement = _git(repository, "rev-parse", "HEAD").stdout.strip()
    _git(repository, "replace", reviewed, replacement)
    _git(repository, "update-ref", "refs/heads/main", reviewed)
    assert _git(repository, "status", "--porcelain=v1", "--untracked-files=all").stdout == ""
    relative = "scripts/mac_bootstrap_patrolgrid_release_tools.sh"
    assert _git(repository, "cat-file", "blob", f"{reviewed}:{relative}").stdout == script.read_text()
    honest = subprocess.run(
        ["/usr/bin/git", "--no-replace-objects", "-C", repository,
         "cat-file", "blob", f"{reviewed}:{relative}"],
        text=True,
        capture_output=True,
        check=True,
    )
    assert honest.stdout != script.read_text()

    result = subprocess.run([script, "install", reviewed], text=True, capture_output=True, check=False)
    assert result.returncode != 0
    assert "replacement refs are forbidden" in result.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac bootstrap trust boundary")
def test_bootstrap_rejects_an_actual_git_graft(tmp_path: Path):
    repository, script, _ = _bootstrap_fixture_repository(tmp_path)
    history = repository / "history.txt"
    history.write_text("second commit\n", encoding="utf-8")
    _git(repository, "add", "history.txt")
    _git(repository, "commit", "--quiet", "-m", "history")
    reviewed = _git(repository, "rev-parse", "HEAD").stdout.strip()
    grafts = repository / ".git/info/grafts"
    grafts.write_text(reviewed + "\n", encoding="ascii")
    assert len(_git(repository, "rev-list", "HEAD").stdout.splitlines()) == 1

    result = subprocess.run([script, "install", reviewed], text=True, capture_output=True, check=False)
    assert result.returncode != 0
    assert "Git grafts are forbidden" in result.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac bootstrap trust boundary")
def test_bootstrap_compares_its_executing_bytes_before_staging_or_sudo(tmp_path: Path):
    repository, script, reviewed = _bootstrap_fixture_repository(tmp_path)
    _git(repository, "update-index", "--assume-unchanged",
         "scripts/mac_bootstrap_patrolgrid_release_tools.sh")
    script.write_text(script.read_text(encoding="utf-8") + "\n# hidden working-tree mutation\n")
    assert _git(repository, "status", "--porcelain=v1", "--untracked-files=all").stdout == ""

    result = subprocess.run([script, "install", reviewed], text=True, capture_output=True, check=False)
    assert result.returncode != 0
    assert "executing bootstrap differs from the reviewed commit" in result.stderr
    assert not (tmp_path / "installed").exists()
    assert BOOTSTRAP_SOURCE.index("executing bootstrap differs from the reviewed commit") < \
        BOOTSTRAP_SOURCE.index("STAGE=\"$($MKTEMP") < BOOTSTRAP_SOURCE.index('"$SUDO" "$INSTALL"')


def test_bootstrap_disables_replacements_for_every_git_read_and_rejects_grafts():
    assert "export GIT_NO_REPLACE_OBJECTS='1'" in BOOTSTRAP_SOURCE
    assert 'git_read() { GIT_NO_REPLACE_OBJECTS=1 "$GIT" --no-replace-objects "$@"; }' in BOOTSTRAP_SOURCE
    assert BOOTSTRAP_SOURCE.count("$GIT") == 2  # fixed binary declaration plus git_read wrapper
    assert "for-each-ref --format='%(refname)' refs/replace" in BOOTSTRAP_SOURCE
    assert '"$git_directory/info/grafts"' in BOOTSTRAP_SOURCE


def test_launcher_ceremony_preserves_marker_into_fresh_checkout_child(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
):
    launcher_path = ROOT / "scripts/mac_patrolgrid_release_launcher.py"
    specification = importlib.util.spec_from_file_location("patrolgrid_ceremony_fixture", launcher_path)
    assert specification and specification.loader
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    observed = tmp_path / "ceremony-marker"

    def fake_checkout(_tag: str, destination: Path) -> str:
        scripts = destination / "scripts"
        scripts.mkdir(parents=True)
        helper = scripts / "mac_decrypt_patrolgrid_release.sh"
        helper.write_text(
            "#!/bin/sh\nprintf '%s|%s' \"$PATROLGRID_TRUSTED_LAUNCHER\" \"$*\" > "
            + shlex.quote(str(observed)) + "\n",
            encoding="utf-8",
        )
        return "a" * 40

    monkeypatch.setattr(module, "verify_tag_and_checkout", fake_checkout)
    arguments = ["ceremony", "patrolgrid-v1.0.0", "/tmp/owner-bundle"]
    assert module.ceremony(arguments) == 0
    assert observed.read_text(encoding="utf-8") == (
        f"{module.SELF}|ceremony patrolgrid-v1.0.0 /tmp/owner-bundle"
    )
