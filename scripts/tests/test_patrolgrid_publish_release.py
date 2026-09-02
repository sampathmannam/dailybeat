from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import stat
import subprocess
import sys

import pytest


ROOT = Path(__file__).resolve().parents[2]
PUBLISHER = ROOT / "scripts/patrolgrid_publish_release.py"


def _load_publisher():
    specification = importlib.util.spec_from_file_location("patrolgrid_output_publisher", PUBLISHER)
    assert specification and specification.loader
    module = importlib.util.module_from_spec(specification)
    sys.modules[specification.name] = module
    specification.loader.exec_module(module)
    return module


def _private_directory(path: Path) -> Path:
    path.mkdir(mode=0o700)
    path.chmod(0o700)
    subprocess.run(["/bin/chmod", "-N", path], check=True)
    return path


def _bundle_stage(tmp_path: Path) -> tuple[Path, Path, dict[str, bytes]]:
    parent = _private_directory(tmp_path / "private-output-parent")
    stage = _private_directory(parent / ".patrolgrid-bundle.fixture")
    staff = _private_directory(stage / "staff")
    owner = _private_directory(stage / "owner")
    expected = {
        "staff/PatrolGrid-1.0.0.apk": b"signed-apk",
        "staff/PatrolGrid-1.0.0-SHA256SUMS.txt": b"checksums",
        "staff/PatrolGrid-1.0.0.spdx.json": b"{}\n",
        "owner/PatrolGrid-1.0.0-mapping.txt": b"owner-only mapping\n",
    }
    for relative, content in expected.items():
        output = stage / relative
        output.write_bytes(content)
        output.chmod(0o600)
        subprocess.run(["/bin/chmod", "-N", output], check=True)
    return stage, parent / "PatrolGrid-1.0.0-owner-bundle", expected


@pytest.mark.skipif(sys.platform != "darwin", reason="macOS renamex_np publication")
def test_real_publisher_atomically_commits_private_owner_bundle(tmp_path: Path):
    stage, destination, expected = _bundle_stage(tmp_path)
    result = subprocess.run(
        ["/usr/bin/python3", "-I", PUBLISHER, stage, destination],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    assert not stage.exists() and destination.is_dir()
    assert stat.S_IMODE(destination.stat().st_mode) == 0o700
    assert {relative: (destination / relative).read_bytes() for relative in expected} == expected
    assert sorted(path.name for path in destination.iterdir()) == ["owner", "staff"]


@pytest.mark.skipif(sys.platform != "darwin", reason="macOS renamex_np publication")
def test_pre_rename_failure_leaves_no_visible_bundle(tmp_path: Path):
    module = _load_publisher()
    stage, destination, _ = _bundle_stage(tmp_path)

    class FailedRename(module.Operations):
        def rename_exclusive(self, source: Path, target: Path) -> None:
            raise OSError(5, "injected rename failure")

    with pytest.raises(module.PublishError, match="no-clobber bundle rename failed"):
        module.publish(stage, destination, operations=FailedRename())
    assert stage.is_dir() and not destination.exists()


@pytest.mark.skipif(sys.platform != "darwin", reason="macOS renamex_np publication")
def test_post_rename_fsync_failure_leaves_one_complete_quarantined_bundle(tmp_path: Path):
    module = _load_publisher()
    stage, destination, expected = _bundle_stage(tmp_path)

    class FailedParentFsync(module.Operations):
        def fsync_directory(self, directory: Path, label: str) -> None:
            if label == "bundle-parent-after-rename":
                raise OSError(5, "injected post-rename fsync failure")
            super().fsync_directory(directory, label)

    with pytest.raises(module.DurabilityError, match="complete bundle is visible"):
        module.publish(stage, destination, operations=FailedParentFsync())
    assert not stage.exists() and destination.is_dir()
    assert {relative: (destination / relative).read_bytes() for relative in expected} == expected


@pytest.mark.skipif(sys.platform != "darwin", reason="macOS process/rename semantics")
def test_sigkill_after_atomic_rename_can_never_expose_half_a_bundle(tmp_path: Path):
    stage, destination, expected = _bundle_stage(tmp_path)
    driver = tmp_path / "kill-after-rename.py"
    driver.write_text(
        """import importlib.util, os, pathlib, sys
spec = importlib.util.spec_from_file_location('publisher', sys.argv[1])
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
class Kill(module.Operations):
    def fsync_directory(self, directory, label):
        if label == 'bundle-parent-after-rename':
            os.kill(os.getpid(), 9)
        super().fsync_directory(directory, label)
module.publish(pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3]), operations=Kill())
""",
        encoding="utf-8",
    )
    result = subprocess.run(
        [sys.executable, "-I", driver, PUBLISHER, stage, destination],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == -9
    assert not stage.exists() and destination.is_dir()
    assert {relative: (destination / relative).read_bytes() for relative in expected} == expected
    assert sorted(path.name for path in destination.iterdir()) == ["owner", "staff"]


@pytest.mark.skipif(sys.platform != "darwin", reason="real macOS ACL semantics")
def test_publisher_rejects_real_extended_acl_before_atomic_rename(tmp_path: Path):
    stage, destination, _ = _bundle_stage(tmp_path)
    mapping = next((stage / "owner").iterdir())
    subprocess.run(["/bin/chmod", "+a", "everyone allow read", mapping], check=True)
    result = subprocess.run(
        ["/usr/bin/python3", "-I", PUBLISHER, stage, destination],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 1
    assert "extended or unreadable ACL" in result.stderr
    assert stage.exists() and not destination.exists()
