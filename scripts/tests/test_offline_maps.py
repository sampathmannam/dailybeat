import hashlib
import importlib.util
import json
from pathlib import Path
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("build_offline_map", ROOT / "scripts/build_offline_map.py")
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


def test_catalog_pins_immutable_parts_and_complete_archive():
    catalog = json.loads((ROOT / "android/app/src/main/assets/offline/tamil-nadu.json").read_text())
    source = json.loads((ROOT / "scripts/maps/sources.json").read_text())
    assert catalog["version"] == source["version"]
    assert sum(part["bytes"] for part in catalog["tiles"]) == catalog["tileBytes"]
    assert all(0 < part["bytes"] <= 512 * 1024 * 1024 for part in catalog["tiles"] + [catalog["assets"]])
    for part in catalog["tiles"] + [catalog["assets"]]:
        assert part["url"] == f'https://github.com/sampathmannam/dailybeat/releases/download/{source["releaseTag"]}/{part["name"]}'
        assert len(bytes.fromhex(part["sha256"])) == 32
    assert hashlib.sha256((ROOT / "scripts/maps/tamil-nadu.geojson").read_bytes()).hexdigest() == source["boundarySha256"]


def test_zip_is_reproducible_and_retains_exact_unpacked_size(tmp_path):
    resources = tmp_path / "resources"
    resources.mkdir()
    (resources / "light.json").write_text("{}")
    (resources / "font.pbf").write_bytes(bytes(range(256)))
    first, second = tmp_path / "one.zip", tmp_path / "two.zip"
    assert builder.zip_resources(resources, first) == 258
    builder.zip_resources(resources, second)
    assert first.read_bytes() == second.read_bytes()
    with zipfile.ZipFile(first) as archive:
        assert set(archive.namelist()) == {"light.json", "font.pbf"}
        assert all(entry.compress_type == zipfile.ZIP_STORED for entry in archive.infolist())


def test_published_manifest_mismatch_stops_before_downloading_assets(tmp_path):
    catalog = json.loads((ROOT / "android/app/src/main/assets/offline/tamil-nadu.json").read_text())
    response = mock.MagicMock()
    response.__enter__.return_value.read.return_value = b'{"tampered": true}'
    with mock.patch.object(builder.urllib.request, "urlopen", return_value=response), mock.patch.object(builder, "fetch") as fetch:
        try:
            builder.reuse_published(catalog, tmp_path)
            raise AssertionError("Accepted a changed published catalog")
        except ValueError:
            pass
        fetch.assert_not_called()


def test_publication_verifies_catalog_before_signing_and_publishes_together():
    workflow = (ROOT / ".github/workflows/publish-release.yml").read_text()
    assert workflow.index("scripts/build_offline_map.py") < workflow.index("Restore permanent signing key")
    assert '--write-catalog' not in workflow
    publish = workflow.split('gh release create "$RELEASE_TAG"', 1)[1]
    assert 'dailybeat-map-build/release-assets/' in publish
    assert 'SHA256SUMS.txt' in publish
    assert 'gh release upload' not in workflow
    assert '--clobber' not in workflow
