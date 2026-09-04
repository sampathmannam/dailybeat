#!/usr/bin/python3 -I
"""Run apkanalyzer only from a copied, hash-pinned command-line-tools graph."""

from __future__ import annotations

import hashlib
import os
import re
import stat
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath

ENTRY = "com.android.tools.apk.analyzer.ApkAnalyzerCli"
LINE = re.compile(r"([0-9a-f]{64})  ([A-Za-z0-9._/+@=-]+\.jar)\Z")


def stop(message: str) -> None:
    raise SystemExit(f"PatrolGrid apkanalyzer trust check stopped: {message}")


def no_acl(path: Path) -> None:
    # "-e" is a macOS extension that lists ACL entries on extra lines. GNU and
    # BusyBox coreutils reject it outright and instead mark an extended ACL with a
    # trailing "+" on the mode field, so ask each platform the way it answers.
    darwin = sys.platform == "darwin"
    listing = subprocess.run(
        ["/bin/ls", "-lde" if darwin else "-ld", str(path)], text=True, capture_output=True,
        check=False, env={"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "LANG": "C", "LC_ALL": "C"},
    )
    lines = listing.stdout.splitlines()
    if listing.returncode or len(lines) != 1:
        stop(f"classpath path has an extended ACL or cannot be inspected: {path}")
    if not darwin and lines[0].split(" ", 1)[0].endswith("+"):
        stop(f"classpath path has an extended ACL or cannot be inspected: {path}")


def expected_entries(manifest: Path) -> list[tuple[str, str]]:
    try:
        lines = manifest.read_text(encoding="ascii").splitlines()
    except OSError as error:
        stop(f"cannot read classpath digest manifest: {error}")
    entries: list[tuple[str, str]] = []
    for line in lines:
        match = LINE.fullmatch(line)
        if not match:
            stop("classpath digest manifest syntax changed")
        digest, relative = match.groups()
        pure = PurePosixPath(relative)
        if pure.is_absolute() or ".." in pure.parts or len(pure.parts) < 2:
            stop("classpath digest manifest contains an unsafe path")
        entries.append((digest, relative))
    if not entries or len({relative for _, relative in entries}) != len(entries):
        stop("classpath digest manifest is empty or has duplicate paths")
    return entries


def jar_classpath(path: Path) -> list[str]:
    try:
        raw = zipfile.ZipFile(path).read("META-INF/MANIFEST.MF").decode("utf-8")
    except (OSError, KeyError, UnicodeDecodeError, zipfile.BadZipFile) as error:
        stop(f"cannot parse pinned apkanalyzer classpath JAR: {error}")
    logical: list[str] = []
    for line in raw.replace("\r\n", "\n").splitlines():
        if line.startswith(" "):
            if not logical:
                stop("classpath JAR manifest has an orphan continuation")
            logical[-1] += line[1:]
        else:
            logical.append(line)
    values = [line[len("Class-Path: "):] for line in logical if line.startswith("Class-Path: ")]
    if len(values) != 1 or not values[0]:
        stop("classpath JAR has no exact Class-Path")
    return values[0].split()


def copy_verified(
    source: Path,
    destination: Path,
    expected_digest: str,
    root: Path,
    destination_mode: int = 0o600,
) -> None:
    try:
        resolved = source.resolve(strict=True)
    except OSError as error:
        stop(f"classpath file cannot resolve: {error}")
    if os.path.commonpath((str(root), str(resolved))) != str(root):
        stop("classpath file resolves outside command-line-tools lib")
    try:
        descriptor = os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    except OSError as error:
        stop(f"cannot safely open classpath file: {error}")
    try:
        metadata = os.fstat(descriptor)
        if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.getuid()
                or metadata.st_mode & 0o022):
            stop("classpath file owner or mode is unsafe")
        no_acl(source)
        digest = hashlib.sha256()
        destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        output = os.open(
            destination,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            destination_mode,
        )
        try:
            with os.fdopen(descriptor, "rb", closefd=False) as input_file, os.fdopen(output, "wb") as output_file:
                for chunk in iter(lambda: input_file.read(1024 * 1024), b""):
                    digest.update(chunk)
                    output_file.write(chunk)
                output_file.flush()
                os.fsync(output_file.fileno())
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
        if digest.hexdigest() != expected_digest:
            destination.unlink(missing_ok=True)
            stop(f"classpath file digest changed: {source}")
        os.chmod(destination, destination_mode)
    finally:
        os.close(descriptor)


def main() -> int:
    if len(sys.argv) < 8:
        stop("usage: <lib> <classpath-jar> <digest-manifest> <java> <build-tools> <stage> -- <args>")
    values = sys.argv[1:]
    lib = Path(values[0]).resolve()
    classpath_jar = Path(values[1])
    manifest = Path(values[2])
    java = Path(values[3])
    build_tools = Path(values[4]).resolve()
    stage = Path(values[5])
    arguments = values[7:]
    if values[6] != "--" or not arguments:
        stop("missing apkanalyzer command separator or arguments")
    if build_tools.name != "36.0.0":
        stop("apkanalyzer must use the pinned Android build-tools 36.0.0 directory")
    expected = expected_entries(manifest)
    relative_paths = [relative for _, relative in expected]
    if jar_classpath(classpath_jar) != relative_paths:
        stop("classpath JAR references a graph different from the digest manifest")
    if stage.exists() or stage.is_symlink():
        stop("private apkanalyzer stage already exists")
    stage.mkdir(mode=0o700)
    tool_home = stage / "sdk/cmdline-tools/latest"
    staged_build_tools = stage / "sdk/build-tools/36.0.0"
    copy_verified(classpath_jar, tool_home / "lib/apkanalyzer-classpath.jar",
                  "6569cf37ed9481aac7b3f6f563fd6cfbe46395dd2d59885ee1174dba9bad063a", lib)
    for digest, relative in expected:
        copy_verified(lib / relative, tool_home / "lib" / relative, digest, lib)
    copy_verified(
        build_tools / "aapt",
        staged_build_tools / "aapt",
        "170717682f714712c5b6854af73cfe37aeda342ff422384e98d67fc1b490f49b",
        build_tools,
        0o500,
    )
    copy_verified(
        build_tools / "lib64/libc++.dylib",
        staged_build_tools / "lib64/libc++.dylib",
        "834cf92eead41eb0c9368604e5ccf1e17b228ce8169d44583cebfaf779f6d27e",
        build_tools,
        0o400,
    )
    copy_verified(
        build_tools / "source.properties",
        staged_build_tools / "source.properties",
        "7dee6632e9ad6cb111da2bb99d747211e27927061b1276d040bb1d71fded5ebb",
        build_tools,
        0o400,
    )
    os.execve(str(java), [str(java), f"-Dcom.android.sdklib.toolsdir={tool_home}", "-classpath",
                          str(tool_home / "lib/apkanalyzer-classpath.jar"), ENTRY, *arguments],
              {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "LANG": "C", "LC_ALL": "C", "HOME": "/Users/sujithsampath"})


if __name__ == "__main__":
    raise SystemExit(main())
