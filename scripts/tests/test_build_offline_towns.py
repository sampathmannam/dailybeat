"""The town converter must fail closed on unreviewed input, without fetching anything."""
import importlib.util
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
