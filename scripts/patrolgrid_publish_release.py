#!/usr/bin/python3 -I
"""Atomically expose one complete owner-only PatrolGrid release bundle."""

from __future__ import annotations

import ctypes
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import sys
from typing import NoReturn


RENAME_EXCL = 0x00000004
DURABILITY_UNCONFIRMED = 21
STERILE_ENV = {
    "HOME": "/Users/sujithsampath",
    "LANG": "C",
    "LC_ALL": "C",
    "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
}


class PublishError(RuntimeError):
    """The bundle was not published."""


class DurabilityError(RuntimeError):
    """The complete bundle is visible but its parent fsync was not confirmed."""


@dataclass(frozen=True)
class Identity:
    device: int
    inode: int
    mode: int
    uid: int
    size: int
    digest: str | None

    @classmethod
    def read(cls, path: Path) -> "Identity":
        metadata = path.lstat()
        value = None
        if stat.S_ISREG(metadata.st_mode):
            checksum = hashlib.sha256()
            with path.open("rb") as source:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    checksum.update(chunk)
            value = checksum.hexdigest()
        return cls(
            metadata.st_dev,
            metadata.st_ino,
            metadata.st_mode,
            metadata.st_uid,
            metadata.st_size,
            value,
        )


class Operations:
    """Injectable system boundary for crash/failure regression tests."""

    def __init__(self) -> None:
        libc = ctypes.CDLL(None, use_errno=True)
        try:
            self._renamex = libc.renamex_np
        except AttributeError as error:
            raise PublishError("renamex_np is unavailable; bundle publication requires macOS") from error
        self._renamex.argtypes = (ctypes.c_char_p, ctypes.c_char_p, ctypes.c_uint)
        self._renamex.restype = ctypes.c_int

    def rename_exclusive(self, source: Path, destination: Path) -> None:
        if self._renamex(os.fsencode(source), os.fsencode(destination), RENAME_EXCL) != 0:
            error = ctypes.get_errno()
            raise OSError(error, os.strerror(error), str(destination))

    def fsync_directory(self, directory: Path, label: str) -> None:
        del label
        descriptor = os.open(directory, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def stop(message: str, status: int = 1) -> NoReturn:
    print(f"PatrolGrid atomic bundle publication stopped: {message}", file=sys.stderr)
    raise SystemExit(status)


def require_no_acl(path: Path) -> None:
    result = subprocess.run(
        ["/bin/ls", "-lde", str(path)],
        env=STERILE_ENV,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode or len(result.stdout.splitlines()) != 1:
        raise PublishError(f"bundle path has an extended or unreadable ACL: {path}")


def require_directory(path: Path, mode: int) -> None:
    metadata = path.lstat()
    if (not stat.S_ISDIR(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode) or
            stat.S_IMODE(metadata.st_mode) != mode or metadata.st_uid != os.getuid()):
        raise PublishError(f"bundle directory is not owner-owned mode {mode:o}: {path}")
    require_no_acl(path)


def require_file(path: Path) -> None:
    metadata = path.lstat()
    if (not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode) or
            stat.S_IMODE(metadata.st_mode) != 0o600 or metadata.st_uid != os.getuid() or
            metadata.st_size <= 0):
        raise PublishError(f"bundle file is not an owner-owned non-empty mode 600 file: {path}")
    require_no_acl(path)


def validate_tree(stage: Path, destination: Path) -> dict[str, Identity]:
    if not stage.is_absolute() or not destination.is_absolute():
        raise PublishError("bundle paths must be absolute")
    parent = destination.parent
    if parent == Path("/") or parent != Path(os.path.realpath(parent)):
        raise PublishError("bundle parent must be a canonical non-root directory")
    parent_mode = stat.S_IMODE(parent.stat().st_mode)
    require_directory(parent, parent_mode)
    if parent.stat().st_mode & 0o022:
        raise PublishError("bundle parent must not be group/world-writable")
    if stage.parent != parent or not stage.name.startswith(".patrolgrid-bundle."):
        raise PublishError("bundle stage is not the ceremony-created same-parent directory")
    if destination.exists() or destination.is_symlink():
        raise PublishError("bundle destination appeared before publication")
    require_directory(stage, 0o700)
    staff = stage / "staff"
    owner = stage / "owner"
    require_directory(staff, 0o700)
    require_directory(owner, 0o700)
    root_entries = sorted(item.name for item in stage.iterdir())
    if root_entries != ["owner", "staff"]:
        raise PublishError("bundle must contain exactly owner/ and staff/ subdirectories")
    staff_entries = sorted(staff.iterdir(), key=lambda item: item.name)
    owner_entries = sorted(owner.iterdir(), key=lambda item: item.name)
    if len(staff_entries) != 3:
        raise PublishError("staff subtree must contain exactly three files")
    if len(owner_entries) != 1 or not owner_entries[0].name.endswith("-mapping.txt"):
        raise PublishError("owner subtree must contain exactly one mapping file")
    identities: dict[str, Identity] = {}
    for path in (stage, staff, owner, *staff_entries, *owner_entries):
        relative = "." if path == stage else path.relative_to(stage).as_posix()
        if path in (stage, staff, owner):
            require_directory(path, 0o700)
        else:
            require_file(path)
        identities[relative] = Identity.read(path)
    if identities["."].device != parent.stat().st_dev:
        raise PublishError("bundle stage is not on its destination filesystem")
    return identities


def fsync_tree(stage: Path, operations: Operations) -> None:
    for path in sorted((item for item in stage.rglob("*") if item.is_file()), key=str):
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    operations.fsync_directory(stage / "staff", "staff-stage")
    operations.fsync_directory(stage / "owner", "owner-stage")
    operations.fsync_directory(stage, "bundle-stage")


def verify_renamed_tree(destination: Path, identities: dict[str, Identity]) -> None:
    for relative, expected in identities.items():
        path = destination if relative == "." else destination / relative
        if Identity.read(path) != expected:
            raise DurabilityError(f"published bundle identity changed: {relative}")
        if relative == "." or stat.S_ISDIR(expected.mode):
            require_directory(path, 0o700)
        else:
            require_file(path)


def publish(
    stage: Path,
    destination: Path,
    *,
    operations: Operations | None = None,
) -> None:
    ops = operations or Operations()
    stage = Path(stage)
    destination = Path(destination)
    identities = validate_tree(stage, destination)
    fsync_tree(stage, ops)
    try:
        ops.rename_exclusive(stage, destination)
    except OSError as error:
        raise PublishError(f"atomic no-clobber bundle rename failed: {error}") from error
    try:
        verify_renamed_tree(destination, identities)
        ops.fsync_directory(destination.parent, "bundle-parent-after-rename")
    except (OSError, PublishError, DurabilityError) as error:
        # A second rename would reintroduce a crash window.  Leave the one
        # complete atomic tree quarantined for an explicit human recheck.
        raise DurabilityError(
            f"complete bundle is visible but durability was not confirmed; quarantine {destination}: {error}"
        ) from error


def main(arguments: list[str]) -> int:
    if len(arguments) != 2:
        stop("usage: patrolgrid_publish_release.py <same-parent-bundle-stage> <new-bundle>", 2)
    try:
        publish(Path(arguments[0]), Path(arguments[1]))
    except DurabilityError as error:
        stop(str(error), DURABILITY_UNCONFIRMED)
    except (OSError, PublishError) as error:
        stop(str(error), 1)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
