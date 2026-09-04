import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
SECURITY = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
LOCK = (ROOT / "scripts/requirements-ci.txt").read_text(encoding="utf-8")


def test_required_ci_uses_only_hash_locked_python_environments():
    assert "pip install -e" not in CI
    assert "pip install -e ." not in CI
    assert CI.count("--require-hashes") == 2
    assert CI.count("--no-deps --only-binary=:all:") == 2
    assert CI.count("-r scripts/requirements-ci.txt") == 2
    assert "PIP_CONFIG_FILE: /dev/null" in CI
    assert re.search(r"actions/setup-python@[0-9a-f]{40}", CI)
    assert "permissions:\n  contents: read" in CI
    assert CI.count("persist-credentials: false") == 2
    assert SECURITY.count("persist-credentials: false") == 2


def test_ci_lock_pins_every_distribution_and_hash():
    assert "git+" not in LOCK
    assert " -e " not in LOCK
    entries = list(
        re.finditer(
            r"(?m)^(?P<name>[a-z0-9][a-z0-9_.-]*)==(?P<version>[^ \\\n]+) \\\n",
            LOCK,
        )
    )
    assert entries
    for index, entry in enumerate(entries):
        end = entries[index + 1].start() if index + 1 < len(entries) else len(LOCK)
        block = LOCK[entry.start() : end]
        assert re.search(r"--hash=sha256:[0-9a-f]{64}", block), entry.group("name")

    pinned = {entry.group("name"): entry.group("version") for entry in entries}
    assert pinned["httpx"] == "0.28.1"
    assert pinned["locust"] == "2.46.4"
    assert pinned["pytest"] == "8.3.5"
