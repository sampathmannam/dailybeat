#!/usr/bin/env python3
"""Run F-Droid's actual source scanner on this committed checkout's store recipe.

Run with the Python environment containing fdroidserver. The source clone, metadata
and config live in a temporary directory; the developer checkout is never patched.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

import yaml


def main():
    root = Path(__file__).resolve().parents[1]
    recipe = yaml.safe_load((root / "fdroid/com.dailybeat.app.yml").read_text())
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    recipe["Repo"] = str(root)
    recipe["Builds"][-1]["commit"] = commit
    with tempfile.TemporaryDirectory(prefix="dailybeat-fdroid-") as folder:
        directory = Path(folder)
        (directory / "metadata").mkdir()
        (directory / "metadata/com.dailybeat.app.yml").write_text(yaml.safe_dump(recipe, sort_keys=False))
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise SystemExit("Set ANDROID_HOME to the Android SDK directory.")
        (directory / "config.yml").write_text(yaml.safe_dump({"sdk_path": sdk}))
        scanner = shutil.which("fdroid")
        if not scanner:
            raise SystemExit("Install fdroidserver and put fdroid on PATH.")
        subprocess.run([scanner, "scanner", "--refresh", "--exit-code", "com.dailybeat.app"],
                       cwd=directory, check=True)


if __name__ == "__main__":
    main()
