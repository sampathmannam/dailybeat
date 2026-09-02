#!/usr/bin/python3 -I
"""Root-installed trust boundary for PatrolGrid release and installation tools."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile


TRUST_ROOT = Path("/Library/Application Support/PatrolGrid/release-tools/current")
SELF = TRUST_ROOT / "bin/patrolgrid-release"
TRUSTED_HELPER = TRUST_ROOT / "scripts/mac_decrypt_patrolgrid_release.sh"
TRUSTED_INSTALLER = TRUST_ROOT / "scripts/mac_install_release_apk.sh"
TRUSTED_COMMON = TRUST_ROOT / "scripts/mac_adb_common.sh"
TRUSTED_MANIFEST_VERIFIER = TRUST_ROOT / "scripts/verify_patrolgrid_release_manifest.py"
TRUSTED_APKANALYZER_EXECUTOR = TRUST_ROOT / "scripts/patrolgrid_apkanalyzer.py"
TRUSTED_OUTPUT_PUBLISHER = TRUST_ROOT / "scripts/patrolgrid_publish_release.py"
TRUSTED_PACKET_VERIFIER = TRUST_ROOT / "scripts/verify_patrolgrid_openpgp_packets.py"
PUBLIC_KEY = TRUST_ROOT / "release/patrolgrid-release-public-key.asc"
CERTIFICATE = TRUST_ROOT / "release/patrolgrid-release-cert.pem"
APKANALYZER_CLASSPATH_MANIFEST = TRUST_ROOT / "release/patrolgrid-apkanalyzer-classpath.sha256"
SOURCE_REPOSITORY = Path("/Users/sujithsampath/Documents/Codex/2026-09-01/ac/dailybeat")
GIT = "/usr/bin/git"
GPG = "/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpg"
PRIMARY = "AA2B9126F5750A6690CEA90410B087D428F60413"
ENCRYPTION = "84AD08D70EC95222457C16CEFEFD926C2C74FB9E"  # gitleaks:allow -- public key fingerprint
PUBLIC_KEY_SHA256 = "0711013ee3dafc2d9245a32cd2a94b94af16ce8c28fd262f267e8c5127861b55"
TAG_PATTERN = re.compile(r"patrolgrid-v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")
TRUSTED_FILES = (
    SELF, TRUSTED_HELPER, TRUSTED_INSTALLER, TRUSTED_COMMON, TRUSTED_MANIFEST_VERIFIER,
    TRUSTED_APKANALYZER_EXECUTOR, TRUSTED_OUTPUT_PUBLISHER, TRUSTED_PACKET_VERIFIER,
    PUBLIC_KEY, CERTIFICATE, APKANALYZER_CLASSPATH_MANIFEST,
)
PINNED_RUNTIME = {
    Path(GPG): "8fc5f38e275f071a09d0446b6514ceef7de4aee1b64477c382ed6ed1a510502e",
    Path("/opt/homebrew/Cellar/gettext/1.0/lib/libintl.8.dylib"): "0c6d618e75fea85cc3d631e164a71766fba9341d19ce1f723300c52e63037c51",
    Path("/opt/homebrew/Cellar/libgcrypt/1.12.2/lib/libgcrypt.20.dylib"): "949a342e6afbf8a4fc0dc8ea90841fa52511ca6f33fd0ef77705cf0ca39b7439",
    Path("/opt/homebrew/Cellar/readline/8.3.3/lib/libreadline.8.3.dylib"): "7d74566dcbd3f64a5ec6266c8285e48f0214a9d2f36ad5f158b4282a3f10b9a9",
    Path("/opt/homebrew/Cellar/libassuan/3.0.2/lib/libassuan.9.dylib"): "1c45b3dd61f6f07249149723358e4d8448af5ced1a6b279a99ddbd7a906d1ff6",
    Path("/opt/homebrew/Cellar/npth/1.8/lib/libnpth.0.dylib"): "f29d1af471de3e3f2c41f1ac212aeb6e14bb37fabf9551a0ebb93b998c5f4665",
    Path("/opt/homebrew/Cellar/libgpg-error/1.61/lib/libgpg-error.0.dylib"): "8d71d115883e68055c0f81356394bb059eefc0829d13b2dd673cba9641fc452d",
}


def stop(message: str) -> "None":
    raise SystemExit(f"PatrolGrid trusted launcher stopped: {message}")


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def require_root_sealed(path: Path) -> None:
    try:
        metadata = path.lstat()
    except FileNotFoundError:
        stop(f"trusted installation is incomplete: {path}")
    if not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
        stop(f"trusted file is not a regular non-symlink: {path}")
    if metadata.st_uid != 0 or metadata.st_mode & 0o022:
        stop(f"trusted file is not root-owned and write-sealed: {path}")
    require_no_extended_acl(path)


def require_no_extended_acl(path: Path) -> None:
    """macOS ACL grants are independent of POSIX ownership/mode bits."""
    try:
        listing = subprocess.run(
            ["/bin/ls", "-lde", str(path)],
            env=clean_environment(),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        ).stdout.splitlines()
    except (OSError, subprocess.CalledProcessError):
        stop(f"could not inspect trusted path ACL: {path}")
    if len(listing) != 1:
        stop(f"trusted path has an extended ACL: {path}")


def verify_trusted_installation() -> None:
    if Path(os.path.realpath(sys.argv[0])) != SELF:
        stop(f"run only the audited root-installed entrypoint: {SELF}")
    for directory in (TRUST_ROOT.parent.parent, TRUST_ROOT.parent, TRUST_ROOT,
                      TRUST_ROOT / "bin", TRUST_ROOT / "scripts", TRUST_ROOT / "release"):
        metadata = directory.lstat()
        if not stat.S_ISDIR(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
            stop(f"trusted directory is unsafe: {directory}")
        if metadata.st_uid != 0 or metadata.st_mode & 0o022:
            stop(f"trusted directory is not root-owned and write-sealed: {directory}")
        require_no_extended_acl(directory)
    for path in TRUSTED_FILES:
        require_root_sealed(path)
    if digest(PUBLIC_KEY) != PUBLIC_KEY_SHA256:
        stop("root-installed PatrolGrid public key digest changed")
    for path, expected in PINNED_RUNTIME.items():
        metadata = path.lstat()
        if not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
            stop(f"pinned GnuPG runtime is unsafe: {path}")
        if metadata.st_mode & 0o022 or digest(path) != expected:
            stop(f"pinned GnuPG runtime changed: {path}")


def clean_environment(*, trusted_child: bool = False) -> dict[str, str]:
    result = {
        "HOME": "/Users/sujithsampath",
        "LANG": "C",
        "LC_ALL": "C",
        "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
        "PATROLGRID_CLEAN_ENV": "1",
        "GIT_NO_REPLACE_OBJECTS": "1",
    }
    if trusted_child:
        result["PATROLGRID_TRUSTED_LAUNCHER"] = str(SELF)
    return result


def run(command: list[str], *, cwd: Path | None = None, capture: bool = False,
        env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        env=env or clean_environment(),
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=True,
    )


def run_bytes(command: list[str], *, env: dict[str, str]) -> bytes:
    return subprocess.run(
        command,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    ).stdout


def git_command(*arguments: str) -> list[str]:
    """Disable local replacement objects independently of Git configuration."""
    return [GIT, "--no-replace-objects", *arguments]


def run_trusted_script(script: Path, arguments: list[str]) -> int:
    """Invoke every release subcommand through the same sterile Bash boundary."""
    return subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", str(script), *arguments],
        env=clean_environment(trusted_child=True),
        check=False,
    ).returncode


def verify_tag_and_checkout(tag: str, destination: Path) -> str:
    git_env = clean_environment()
    git_env.update({
        "GIT_CONFIG_GLOBAL": "/dev/null",
        "GIT_CONFIG_SYSTEM": "/dev/null",
        "GIT_TERMINAL_PROMPT": "0",
    })
    run(git_command("init", "--quiet", str(destination)), env=git_env)
    run(git_command("-C", str(destination), "remote", "add", "origin",
                    "https://github.com/sampathmannam/dailybeat.git"), env=git_env)
    run(git_command(
        "-C", str(destination), "fetch", "--force", "--no-tags", "--depth=1",
        "origin", f"+refs/tags/{tag}:refs/tags/{tag}",
        "+refs/heads/main:refs/remotes/origin/main",
    ), env=git_env)
    if run(git_command("-C", str(destination), "for-each-ref", "--format=%(refname)",
                       "refs/replace"), capture=True, env=git_env).stdout:
        stop("fresh release checkout contains a Git replacement ref")
    if (destination / ".git/info/grafts").exists() or (destination / ".git/info/grafts").is_symlink():
        stop("fresh release checkout contains a Git graft")
    tag_type = run(git_command("-C", str(destination), "cat-file", "-t", f"refs/tags/{tag}"),
                   capture=True, env=git_env).stdout.strip()
    if tag_type != "tag":
        stop("release tag is not annotated")
    raw_tag = run_bytes(
        git_command("-C", str(destination), "cat-file", "tag", f"refs/tags/{tag}"),
        env=git_env,
    )
    marker = b"-----BEGIN PGP SIGNATURE-----"
    marker_index = raw_tag.find(marker)
    if marker_index <= 0 or raw_tag.find(marker, marker_index + 1) != -1:
        stop("annotated tag does not contain one exact OpenPGP signature")
    verification_home = destination.parent / "tag-gpg"
    verification_home.mkdir(mode=0o700)
    payload = destination.parent / "tag-payload"
    signature = destination.parent / "tag-signature.asc"
    status_file = destination.parent / "tag-status"
    payload.write_bytes(raw_tag[:marker_index])
    signature.write_bytes(raw_tag[marker_index:])
    os.chmod(payload, 0o600)
    os.chmod(signature, 0o600)
    run([GPG, "--homedir", str(verification_home), "--no-options", "--batch",
         "--no-auto-key-retrieve", "--import", str(PUBLIC_KEY)], env=git_env)
    listing = run([GPG, "--homedir", str(verification_home), "--no-options", "--batch",
                   "--with-colons", "--fingerprint", "--fingerprint", "--list-keys", PRIMARY],
                  capture=True, env=git_env).stdout
    fingerprints = [line.split(":")[9] for line in listing.splitlines() if line.startswith("fpr:")]
    if fingerprints != [PRIMARY, ENCRYPTION]:
        stop("root-installed public key has an unexpected fingerprint set")
    verified = subprocess.run(
        [GPG, "--homedir", str(verification_home), "--no-options", "--batch",
         "--no-auto-key-retrieve", "--status-file", str(status_file),
         "--verify", str(signature), str(payload)],
        env=git_env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if verified.returncode != 0:
        stop("release tag signature is invalid under the root-installed public key")
    valid = [line.split()[2] for line in status_file.read_text().splitlines()
             if line.startswith("[GNUPG:] VALIDSIG ")]
    if valid != [PRIMARY]:
        stop("release tag signer is not the exact PatrolGrid primary key")
    commit = run(git_command("-C", str(destination), "rev-parse", f"{tag}^{{commit}}"),
                 capture=True, env=git_env).stdout.strip()
    main = run(git_command("-C", str(destination), "rev-parse", "refs/remotes/origin/main"),
               capture=True, env=git_env).stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40}", commit) or commit != main:
        stop("signed tag is not the fetched current main HEAD")
    run(git_command("-C", str(destination), "-c", "advice.detachedHead=false", "checkout",
                    "--quiet", "--detach", commit), env=git_env)
    if run(git_command("-C", str(destination), "status", "--porcelain=v1",
                       "--untracked-files=all"),
           capture=True, env=git_env).stdout:
        stop("fresh signed-tag checkout is not clean")
    signed_inputs = {
        "scripts/mac_decrypt_patrolgrid_release.sh": TRUSTED_HELPER,
        "scripts/verify_patrolgrid_release_manifest.py": TRUSTED_MANIFEST_VERIFIER,
        "scripts/patrolgrid_apkanalyzer.py": TRUSTED_APKANALYZER_EXECUTOR,
        "scripts/patrolgrid_publish_release.py": TRUSTED_OUTPUT_PUBLISHER,
        "scripts/verify_patrolgrid_openpgp_packets.py": TRUSTED_PACKET_VERIFIER,
        "release/patrolgrid-release-public-key.asc": PUBLIC_KEY,
        "release/patrolgrid-release-cert.pem": CERTIFICATE,
        "release/patrolgrid-apkanalyzer-classpath.sha256": APKANALYZER_CLASSPATH_MANIFEST,
    }
    for relative, trusted in signed_inputs.items():
        path = destination / relative
        metadata = path.lstat()
        if not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
            stop(f"signed-tag release input is unsafe: {relative}")
        if digest(path) != digest(trusted):
            stop(f"signed-tag release input differs from the separately audited root copy: {relative}")
    return commit


def ceremony(arguments: list[str]) -> int:
    if len(arguments) != 3 or not TAG_PATTERN.fullmatch(arguments[1]):
        stop("usage: patrolgrid-release ceremony patrolgrid-vX.Y.Z <new-owner-bundle-dir>")
    scratch = Path(tempfile.mkdtemp(prefix=".patrolgrid-launcher.", dir="/tmp"))
    os.chmod(scratch, 0o700)
    try:
        checkout = scratch / "checkout"
        verify_tag_and_checkout(arguments[1], checkout)
        helper = checkout / "scripts/mac_decrypt_patrolgrid_release.sh"
        return run_trusted_script(helper, arguments)
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def main() -> int:
    verify_trusted_installation()
    arguments = sys.argv[1:]
    if arguments == ["check"]:
        return run_trusted_script(TRUSTED_HELPER, arguments)
    if len(arguments) == 2 and arguments[0] == "create-tag" and TAG_PATTERN.fullmatch(arguments[1]):
        return run_trusted_script(TRUSTED_HELPER, arguments)
    if arguments and arguments[0] == "ceremony":
        return ceremony(arguments)
    if arguments and arguments[0] == "install" and len(arguments) in (2, 3):
        return run_trusted_script(TRUSTED_INSTALLER, arguments[1:])
    stop("usage: patrolgrid-release check | create-tag <tag> | ceremony <tag> <owner-bundle-dir> | install <staff-dir> [serial]")


if __name__ == "__main__":
    raise SystemExit(main())
