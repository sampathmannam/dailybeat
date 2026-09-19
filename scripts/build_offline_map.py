#!/usr/bin/env python3
"""Build the pinned Tamil Nadu package; never fetch tiles from public OSM raster servers.

The APK embeds the catalog. Release publication MUST verify this exact catalog before shipping.
All output and downloaded tools belong in --work-dir, outside the source checkout.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import platform
import shutil
import subprocess
import tarfile
import urllib.request
import urllib.error
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PART_SIZE = 512 * 1024 * 1024
USER_AGENT = "DailyBeatMapBuilder/1.0 (+https://github.com/sampathmannam/dailybeat)"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fetch(url: str, target: Path, expected_hash: str | None = None) -> None:
    if not target.exists() or expected_hash and sha256(target) != expected_hash:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        partial = target.with_suffix(target.suffix + ".partial")
        with urllib.request.urlopen(request, timeout=60) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
        partial.replace(target)
    if expected_hash and sha256(target) != expected_hash:
        raise ValueError(f"Checksum mismatch: {target.name}")


def zip_resources(directory: Path, target: Path) -> int:
    total = 0
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED) as archive:
        for file in sorted(directory.rglob("*")):
            if not file.is_file():
                continue
            info = zipfile.ZipInfo(file.relative_to(directory).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o100644 << 16
            data = file.read_bytes()
            total += len(data)
            archive.writestr(info, data)
    return total


def reuse_published(catalog: dict, output: Path) -> bool:
    """Future APKs can reuse immutable packages after the upstream daily extract expires."""
    manifest_url = catalog["assets"]["url"].rsplit("/", 1)[0] + "/TamilNadu-map-manifest.json"
    try:
        with urllib.request.urlopen(urllib.request.Request(manifest_url, headers={"User-Agent": USER_AGENT}), timeout=60) as response:
            published = json.loads(response.read(65537))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return False  # The first publication builds the package below.
        raise
    if published != catalog:
        raise ValueError("Published manifest differs from the catalog embedded in this APK")
    output.mkdir(parents=True, exist_ok=True)
    for part in catalog["tiles"] + [catalog["assets"]]:
        target = output / part["name"]
        fetch(part["url"], target, part["sha256"])
        if target.stat().st_size != part["bytes"]:
            raise ValueError("Published package size differs from the signed catalog")
    (output / "TamilNadu-map-manifest.json").write_text(json.dumps(catalog, indent=2) + "\n")
    (output / "MAP-SHA256SUMS.txt").write_text("".join(f"{sha256(f)}  {f.name}\n" for f in sorted(output.iterdir()) if f.name != "MAP-SHA256SUMS.txt"))
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--prefer-published", action="store_true", help="Reuse verified immutable assets when already published")
    parser.add_argument("--write-catalog", action="store_true", help="Explicitly update the source catalog before building an APK")
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    config = json.loads((ROOT / "scripts/maps/sources.json").read_text())
    if args.prefer_published and not args.write_catalog:
        embedded_catalog = json.loads((ROOT / "android/app/src/main/assets/offline/tamil-nadu.json").read_text())
        if reuse_published(embedded_catalog, work / "release-assets"):
            print("Verified immutable map assets against the embedded APK catalog")
            return
    boundary = ROOT / "scripts/maps/tamil-nadu.geojson"
    assert sha256(boundary) == config["boundarySha256"]
    tool_dir = work / "tools"
    tool_dir.mkdir(exist_ok=True)
    system = platform.system()
    arch = "arm64" if platform.machine() in ("aarch64", "arm64") else "x86_64"
    tool_name = f"go-pmtiles-1.31.2_Darwin_{arch}.zip" if system == "Darwin" else f"go-pmtiles_1.31.2_Linux_{arch}.tar.gz"
    tool = config["pmtiles"][tool_name]
    archive = tool_dir / tool_name
    fetch(tool["url"], archive, tool["sha256"])
    if tool_name.endswith(".zip"):
        with zipfile.ZipFile(archive) as z:
            (tool_dir / "pmtiles").write_bytes(z.read("pmtiles"))
    else:
        with tarfile.open(archive) as tar:
            member = next(m for m in tar.getmembers() if Path(m.name).name == "pmtiles")
            (tool_dir / "pmtiles").write_bytes(tar.extractfile(member).read())
    pmtiles = tool_dir / "pmtiles"
    pmtiles.chmod(0o755)
    tiles = work / "tamil-nadu.pmtiles"
    if not tiles.exists():
        subprocess.run(["./pmtiles", "extract", "https://build.protomaps.com/" + config["source"]["objectName"],
            str(tiles), f"--region={boundary}", "--maxzoom=15", "--download-threads=2"], cwd=tool_dir, shell=False, check=True)
    subprocess.run(["./pmtiles", "verify", str(tiles)], cwd=tool_dir, shell=False, check=True)
    header = json.loads(subprocess.check_output(["./pmtiles", "show", str(tiles), "--header-json"], cwd=tool_dir, shell=False))
    assert header["minzoom"] == 0 and header["maxzoom"] == 15 and header["tile_type"] == "mvt"

    asset_archive = work / "basemaps-assets.zip"
    fetch("https://api.github.com/repos/protomaps/basemaps-assets/zipball/" + config["assetsCommit"],
          asset_archive, config["assetsZipSha256"])
    resources = work / "resources"
    if resources.exists():
        shutil.rmtree(resources)
    resources.mkdir()
    with zipfile.ZipFile(asset_archive) as z:
        for item in z.infolist():
            relative = Path(*Path(item.filename).parts[1:])
            if item.is_dir() or not relative.parts:
                continue
            if relative.parts[0] == "fonts" or relative.as_posix().startswith("sprites/v4/") or relative.as_posix() == "sprites/LICENSE":
                assert ".." not in relative.parts and not relative.is_absolute()
                output = resources / relative
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(z.read(item))
    package_file = work / "style.tgz"
    fetch(config["style"]["tarball"], package_file)
    integrity = "sha512-" + base64.b64encode(hashlib.sha512(package_file.read_bytes()).digest()).decode()
    assert integrity == config["style"]["integrity"]
    style_module = work / "style.cjs"
    with tarfile.open(package_file) as tar:
        style_module.write_bytes(tar.extractfile("package/dist/cjs/index.cjs").read())
    subprocess.run(["node", str(ROOT / "scripts/maps/render_style.mjs"), str(style_module), str(resources)], check=True)
    shutil.copyfile(boundary, resources / "region.geojson")
    shutil.copyfile(ROOT / "scripts/maps/PROTOMAPS-LICENSE.md", resources / "PROTOMAPS-LICENSE.md")
    (resources / "LICENSES.txt").write_text(
        "Map data © OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright\n"
        "Basemap produced by Protomaps; style BSD-3-Clause. https://github.com/protomaps/basemaps\n"
        "Fonts: SIL Open Font License (fonts/OFL.txt). Sprites: MIT, derived from tangrams/icons.\n"
        "Tamil Nadu boundary: DataMeet India / Election Commission of India, via geoBoundaries.\n"
        "Creative Commons Attribution 2.5 India. https://github.com/datameet/maps\n")
    output_dir = work / "release-assets"
    output_dir.mkdir(exist_ok=True)
    resource_file = output_dir / "TamilNadu-map-assets.zip"
    unpacked = zip_resources(resources, resource_file)
    release_url = "https://github.com/sampathmannam/dailybeat/releases/download/" + config["releaseTag"] + "/"

    def record(file: Path) -> dict:
        return {"name": file.name, "url": release_url + file.name, "bytes": file.stat().st_size, "sha256": sha256(file)}

    parts = []
    with tiles.open("rb") as source:
        for index in range(32):
            data = source.read(PART_SIZE)
            if not data:
                break
            file = output_dir / f"TamilNadu-map.pmtiles.part{index:02d}"
            file.write_bytes(data)
            parts.append(record(file))
        assert not source.read(1), "Map exceeds the supported package size"
    date = config["source"]["objectName"][:8]
    catalog = {"schema": 1, "region": "tamil-nadu", "version": config["version"],
        "dataDate": f"{date[:4]}-{date[4:6]}-{date[6:8]}", "bounds": header["bounds"],
        "tileBytes": tiles.stat().st_size, "tileSha256": sha256(tiles), "tiles": parts,
        "assets": record(resource_file), "unpackedAssetBytes": unpacked}
    text = json.dumps(catalog, indent=2) + "\n"
    embedded = ROOT / "android/app/src/main/assets/offline/tamil-nadu.json"
    if args.write_catalog:
        embedded.parent.mkdir(parents=True, exist_ok=True)
        embedded.write_text(text)
    elif json.loads(embedded.read_text()) != catalog:
        raise ValueError("Rebuilt map differs from the catalog embedded in the APK; refusing publication")
    (output_dir / "TamilNadu-map-manifest.json").write_text(text)
    (output_dir / "MAP-SHA256SUMS.txt").write_text("".join(f"{sha256(f)}  {f.name}\n" for f in sorted(output_dir.iterdir()) if f.name != "MAP-SHA256SUMS.txt"))
    print(json.dumps({"downloadBytes": catalog["tileBytes"] + catalog["assets"]["bytes"], "unpackedAssetBytes": unpacked,
        "version": catalog["version"], "output": str(output_dir)}, indent=2))


if __name__ == "__main__":
    main()
