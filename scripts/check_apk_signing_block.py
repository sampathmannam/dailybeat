#!/usr/bin/env python3
"""Reject opaque APK signing-block payloads; apksigner separately verifies signatures.

Format: https://source.android.com/docs/security/features/apksigning/v2
Only standard v2/v3/v3.1 signatures and verity padding belong in our APKs.
"""
import argparse
from pathlib import Path
import struct


ALLOWED_IDS = {0x7109871A, 0xF05368C0, 0x1B93AD61, 0x42726577}
SIGNATURE_IDS = ALLOWED_IDS - {0x42726577}
OPAQUE_NAMES = {0x504B4453: "Dependency metadata", 0x2146444E: "Google Play Frosting",
                0x71777777: "Meituan payload"}
MAGIC = b"APK Sig Block 42"


def verify_signing_block(apk: Path, require_signed: bool = False) -> set[int]:
    with apk.open("rb") as stream:
        size = stream.seek(0, 2)
        tail_size = min(size, 65535 + 22)
        stream.seek(size - tail_size)
        tail = stream.read(tail_size)
        eocd = tail.rfind(b"PK\x05\x06")
        if eocd < 0 or eocd + 22 > len(tail):
            raise ValueError("Missing ZIP end record")
        _, disk, cd_disk, entries, total, cd_size, cd_offset, comment = struct.unpack_from(
            "<4s4H2IH", tail, eocd)
        if disk or cd_disk or entries != total or total == 65535:
            raise ValueError("Split/ZIP64 APKs are unsupported")
        if eocd + 22 + comment != len(tail) or cd_offset + cd_size != size - tail_size + eocd:
            raise ValueError("Invalid ZIP central directory bounds")
        if cd_offset < 24:
            if require_signed:
                raise ValueError("Missing APK signing block")
            return set()
        stream.seek(cd_offset - 24)
        footer = stream.read(24)
        if footer[8:] != MAGIC:
            if require_signed:
                raise ValueError("Missing APK signing block")
            return set()
        block_size = struct.unpack_from("<Q", footer)[0]
        start = cd_offset - block_size - 8
        if block_size < 24 or start < 0:
            raise ValueError("Invalid APK signing block size")
        stream.seek(start)
        if struct.unpack("<Q", stream.read(8))[0] != block_size:
            raise ValueError("APK signing block sizes disagree")
        end = cd_offset - 24
        ids = set()
        while stream.tell() < end:
            if end - stream.tell() < 12:
                raise ValueError("Truncated APK signing entry")
            length, block_id = struct.unpack("<QI", stream.read(12))
            if length < 4 or length - 4 > end - stream.tell():
                raise ValueError("Invalid APK signing entry length")
            if block_id in ids:
                raise ValueError("Duplicate APK signing entry")
            if block_id not in ALLOWED_IDS:
                name = OPAQUE_NAMES.get(block_id, f"unknown ID 0x{block_id:08x}")
                raise ValueError(f"Opaque APK signing block rejected: {name}")
            ids.add(block_id)
            stream.seek(length - 4, 1)
        if require_signed and not ids & SIGNATURE_IDS:
            raise ValueError("APK signing block has no signature entry")
        return ids


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--require-signed", action="store_true")
    args = parser.parse_args()
    try:
        ids = verify_signing_block(args.apk, args.require_signed)
    except (OSError, ValueError, struct.error) as error:
        raise SystemExit(str(error)) from error
    print("APK signing blocks verified:", ", ".join(f"0x{x:08x}" for x in sorted(ids)) or "unsigned")


if __name__ == "__main__":
    main()
