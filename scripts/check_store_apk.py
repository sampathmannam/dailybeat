#!/usr/bin/env python3
"""Reject release packaging regressions in the F-Droid APK (signed or unsigned)."""
from pathlib import Path
import argparse
import hashlib
import re
import subprocess
import zipfile

from check_apk_signing_block import verify_signing_block


def verify(apk: Path, aapt: Path):
    verify_signing_block(apk)
    manifest = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"], text=True)
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    assert "name='com.dailybeat.app'" in badging, "Wrong store application ID"
    assert "application-debuggable" not in badging, "Store APK must not be debuggable"
    assert "ACTIVITY_RECOGNITION" not in manifest, "Google activity permissions leaked into the store build"
    assert "REQUEST_INSTALL_PACKAGES" not in manifest, "Store build must not install APKs"
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        # Release resource optimization shortens filenames. Verify the bytes instead.
        texts = {hashlib.sha256(archive.read(n)).digest() for n in names if n.startswith("res/") and n.endswith(".txt")}
        root = Path(__file__).resolve().parents[1]
        for source in (root / "LICENSE", root / "android/app/src/main/res/raw/maplibre_notices.txt"):
            assert hashlib.sha256(source.read_bytes()).digest() in texts, f"Bundled licence missing: {source.name}"
        assert "assets/offline/tamil-nadu.json" in names, "Map catalog missing"
        for name in names:
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            data = archive.read(name)
            assert b"com/google/android/gms/" not in data, "Google Play Services code found"
            assert b"com/google/firebase/" not in data, "Firebase code found"
            assert not re.search(rb"https://[a-z0-9-]+\.supabase\.co", data), "Managed backend configuration leaked into the store APK"
    print("Store APK: release identity, permissions, licences and Google-free code verified.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--aapt", type=Path, required=True)
    args = parser.parse_args()
    verify(args.apk, args.aapt)


if __name__ == "__main__":
    main()
