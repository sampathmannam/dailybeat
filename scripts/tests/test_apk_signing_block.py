import importlib.util
import io
from pathlib import Path
import struct
import zipfile

import pytest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("signing_gate", ROOT / "scripts/check_apk_signing_block.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def apk(tmp_path, pairs=None, corrupt=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"test fixture, not an installable app")
    data = buffer.getvalue()
    if pairs is not None:
        eocd = len(data) - 22
        cd = struct.unpack_from("<I", data, eocd + 16)[0]
        values = b"".join(struct.pack("<QI", len(value) + 4, key) + value for key, value in pairs)
        size = len(values) + 24
        block = struct.pack("<Q", size) + values + struct.pack("<Q", size) + gate.MAGIC
        if corrupt:
            block = corrupt(block)
        data = bytearray(data[:cd] + block + data[cd:])
        struct.pack_into("<I", data, eocd + len(block) + 16, cd + len(block))
    path = tmp_path / "fixture.apk"
    path.write_bytes(data)
    return path


def test_unsigned_is_allowed_only_when_not_requiring_signature(tmp_path):
    path = apk(tmp_path)
    assert gate.verify_signing_block(path) == set()
    with pytest.raises(ValueError, match="Missing APK signing block"):
        gate.verify_signing_block(path, require_signed=True)


def test_standard_signature_and_padding_entries_are_accepted(tmp_path):
    pairs = [(key, b"fixture") for key in gate.ALLOWED_IDS]
    assert gate.verify_signing_block(apk(tmp_path, pairs), True) == gate.ALLOWED_IDS


@pytest.mark.parametrize("key", [0x504B4453, 0x2146444E, 0x71777777, 0xDEADBEEF])
def test_opaque_metadata_after_a_signature_is_rejected(tmp_path, key):
    with pytest.raises(ValueError, match="Opaque APK signing block"):
        gate.verify_signing_block(apk(tmp_path, [(0x7109871A, b"signature"), (key, b"opaque")]), True)


def test_padding_is_not_a_signature(tmp_path):
    with pytest.raises(ValueError, match="no signature entry"):
        gate.verify_signing_block(apk(tmp_path, [(0x42726577, b"\0" * 32)]), True)


def test_duplicate_signature_entries_are_rejected(tmp_path):
    with pytest.raises(ValueError, match="Duplicate"):
        gate.verify_signing_block(apk(tmp_path, [(0x7109871A, b"x")] * 2), True)


@pytest.mark.parametrize("corrupt, message", [
    (lambda b: struct.pack("<Q", 1) + b[8:], "sizes disagree"),
    (lambda b: b[:8] + struct.pack("<Q", 3) + b[16:], "entry length"),
    (lambda b: b[:8] + struct.pack("<Q", 2**63) + b[16:], "entry length"),
    (lambda b: b[:-24] + struct.pack("<Q", 2**63) + b[-16:], "block size"),
])
def test_malformed_signing_entries_fail_closed(tmp_path, corrupt, message):
    with pytest.raises(ValueError, match=message):
        gate.verify_signing_block(apk(tmp_path, [(0x7109871A, b"sig")], corrupt), True)


def test_invalid_zip_fails_closed(tmp_path):
    path = tmp_path / "bad.apk"
    path.write_bytes(b"not an APK")
    with pytest.raises(ValueError, match="Missing ZIP"):
        gate.verify_signing_block(path, True)
