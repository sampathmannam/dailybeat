"""The town converter must fail closed on unreviewed input, without fetching anything."""
import importlib.util
import hashlib
from pathlib import Path
import pytest

spec = importlib.util.spec_from_file_location(
    "build_offline_towns", Path(__file__).resolve().parents[1] / "build_offline_towns.py")
converter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(converter)


@pytest.mark.parametrize("unreviewed", [b"", b"not a zip", b"PK\x03\x04"])
def test_rejects_unreviewed_source_before_parsing(unreviewed):
    with pytest.raises(ValueError, match="Not the reviewed GeoNames snapshot"):
        converter.convert(unreviewed)


def test_repacked_reviewed_index_matches_published_resource():
    root = Path(__file__).resolve().parents[2]
    legacy = (root / "data/reviewed/offline-towns-v1.dat.gz").read_bytes()
    repacked, count = converter.repack_legacy(legacy)
    assert count == 31638
    assert hashlib.sha256(repacked).hexdigest() == (
        "61e82640f44c0cf84bfd13cf1c74a21134d6c0ef84066cd37bd70c129e213929")
    assert repacked == (root / "android/app/src/main/resources/offline-towns-v2.dat.gz").read_bytes()
