#!/usr/bin/env python3
"""Reject 64-bit native libraries incompatible with Android's 16 KiB page sizes."""
import argparse
import struct
import zipfile


def check(apk):
    count = 0
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.endswith(".so") or not any(abi in name for abi in ("/arm64-v8a/", "/x86_64/")):
                continue
            data = archive.read(name)
            if data[:5] != b"\x7fELF\x02" or data[5] not in (1, 2):
                raise ValueError(f"Invalid 64-bit ELF: {name}")
            endian = "<" if data[5] == 1 else ">"
            offset = struct.unpack_from(endian + "Q", data, 32)[0]
            size, entries = struct.unpack_from(endian + "HH", data, 54)
            if size < 56 or offset + size * entries > len(data):
                raise ValueError(f"Invalid program headers: {name}")
            segments = 0
            for index in range(entries):
                header = offset + size * index
                if struct.unpack_from(endian + "I", data, header)[0] != 1:
                    continue
                segments += 1
                file_offset, address = struct.unpack_from(endian + "QQ", data, header + 8)
                alignment = struct.unpack_from(endian + "Q", data, header + 48)[0]
                if alignment < 16384 or file_offset % 16384 != address % 16384:
                    raise ValueError(f"Not compatible with 16 KiB pages: {name}")
            if not segments:
                raise ValueError(f"No loadable segments: {name}")
            count += 1
    if count == 0:
        raise ValueError("Expected 64-bit native libraries in this DailyBeat APK")
    print(f"Verified 16 KiB ELF alignment for {count} native libraries")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    check(parser.parse_args().apk)
