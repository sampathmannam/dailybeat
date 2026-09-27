#!/usr/bin/env python3
"""Deterministic public-data conversion. No network, credentials or user coordinates.

Input: the pinned GeoNames cities15000 ZIP (CC BY 4.0).
Output: gzip-wrapped DBT1 binary, big endian; count then balanced 3D kd-tree records
in median-array order: x/y/z doubles on the unit sphere, UTF-8 name and country,
each prefixed by an unsigned 16-bit byte length. See docs/offline-place-names.md.
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

    output = io.BytesIO()
    output.write(b"DBT1" + struct.pack(">I", len(rows)))
    for vector, name, country, _ in order(rows):
        output.write(struct.pack(">ddd", *vector))
        for value in (name, country):
            data = value.encode("utf-8")
            output.write(struct.pack(">H", len(data)) + data)
    # Fixed header, timestamp and compression for reproducible APK inputs.
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
        stream.write(output.getvalue())
    return compressed.getvalue(), len(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--check", action="store_true", help="Verify the committed resource without writing")
    args = parser.parse_args()
    data, count = convert(args.archive.read_bytes())
    if args.check:
        if args.output.read_bytes() != data:
            raise SystemExit("Generated resource differs")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(data)
    print(f"{count} settlements, {len(data)} bytes, sha256={hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
