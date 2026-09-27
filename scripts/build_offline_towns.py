#!/usr/bin/env python3
"""Deterministic public-data conversion. No network, credentials or user coordinates.

Input: the pinned GeoNames cities15000 ZIP (CC BY 4.0), or the pinned DBT1 extract.
Output: gzip-wrapped DBT2 binary, big endian; count then contiguous x/y/z doubles
in median-array order, followed by UTF-8 name and country records with unsigned
16-bit byte lengths. See docs/offline-place-names.md.
"""
import argparse
import gzip
import hashlib
import io
import math
from pathlib import Path
import struct
import zipfile

SOURCE_SHA256 = "9c1f26fa632212ad77e5b82b6d5df3b019a3c119e423efe59d7224a186f9c9f4"
LEGACY_SHA256 = "3a36460fd221f2254fd533cf8ef2686f55a386e401abd82da29bd89f32f1f49c"
SETTLEMENT_CODES = {"PPL", "PPLA", "PPLA2", "PPLA3", "PPLA4", "PPLA5", "PPLC"}


def convert(archive: bytes) -> tuple[bytes, int]:
    if hashlib.sha256(archive).hexdigest() != SOURCE_SHA256:
        raise ValueError("Not the reviewed GeoNames snapshot; review and pin a new source explicitly")
    rows = []
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        for line in zipped.read("cities15000.txt").decode("utf-8").splitlines():
            fields = line.split("\t")
            if fields[6] != "P" or fields[7] not in SETTLEMENT_CODES:
                continue
            lat, lon = map(float, fields[4:6])
            name, country = (fields[2] or fields[1]).strip(), fields[8]
            if not (-90 <= lat <= 90 and -180 <= lon <= 180 and name and len(country) == 2):
                raise ValueError("Invalid settlement record")
            phi, lam = math.radians(lat), math.radians(lon)
            rows.append(((math.cos(phi) * math.cos(lam), math.cos(phi) * math.sin(lam), math.sin(phi)),
                         name, country, int(fields[0])))

    def order(points, depth=0):
        if not points:
            return []
        axis = depth % 3
        points.sort(key=lambda point: (point[0][axis], point[3]))
        mid = len(points) // 2
        return order(points[:mid], depth + 1) + [points[mid]] + order(points[mid + 1:], depth + 1)

    ordered = order(rows)
    output = io.BytesIO()
    output.write(b"DBT2" + struct.pack(">I", len(ordered)))
    for vector, _, _, _ in ordered:
        output.write(struct.pack(">ddd", *vector))
    for _, name, country, _ in ordered:
        for value in (name, country):
            data = value.encode("utf-8")
            output.write(struct.pack(">H", len(data)) + data)
    # Fixed header, timestamp and compression for reproducible APK inputs.
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
        stream.write(output.getvalue())
    return compressed.getvalue(), len(rows)


def repack_legacy(archive: bytes) -> tuple[bytes, int]:
    """Repack the reviewed DBT1 bytes without changing any vector or label."""
    if hashlib.sha256(archive).hexdigest() != LEGACY_SHA256:
        raise ValueError("Not the reviewed DBT1 extract")
    source = io.BytesIO(gzip.decompress(archive))
    if source.read(4) != b"DBT1":
        raise ValueError("Invalid DBT1 header")
    count = struct.unpack(">I", source.read(4))[0]
    if count != 31638:
        raise ValueError("Invalid DBT1 count")
    vectors = io.BytesIO()
    labels = io.BytesIO()
    for _ in range(count):
        vector = source.read(24)
        if len(vector) != 24:
            raise ValueError("Truncated DBT1 vector")
        vectors.write(vector)
        for _ in range(2):
            length_bytes = source.read(2)
            if len(length_bytes) != 2:
                raise ValueError("Truncated DBT1 length")
            length = struct.unpack(">H", length_bytes)[0]
            value = source.read(length)
            if len(value) != length:
                raise ValueError("Truncated DBT1 value")
            labels.write(length_bytes + value)
    if source.read(1):
        raise ValueError("Trailing DBT1 bytes")
    result = b"DBT2" + struct.pack(">I", count) + vectors.getvalue() + labels.getvalue()
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
        stream.write(result)
    return compressed.getvalue(), count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--check", action="store_true", help="Verify the committed resource without writing")
    parser.add_argument("--legacy-index", action="store_true", help="Use the reviewed DBT1 extract")
    args = parser.parse_args()
    data, count = (repack_legacy if args.legacy_index else convert)(args.archive.read_bytes())
    if args.check:
        if args.output.read_bytes() != data:
            raise SystemExit("Generated resource differs")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(data)
    print(f"{count} settlements, {len(data)} bytes, sha256={hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
