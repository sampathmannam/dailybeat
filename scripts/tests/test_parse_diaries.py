"""Tests for parse_diaries.py"""

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PARSE_SCRIPT = ROOT / "scripts" / "parse_diaries.py"
SAMPLE_RAW = ROOT / "data" / "samples" / "example_raw_day.txt"
SAMPLE_JSONL = ROOT / "data" / "samples" / "diary_train.sample.jsonl"


def run_parse(input_dir: Path, output: Path, merge: list[Path] = None) -> subprocess.CompletedProcess:
    cmd = [
        sys.executable,
        str(PARSE_SCRIPT),
        "--input",
        str(input_dir),
        "--output",
        str(output),
        "--min-rows",
        "1",
    ]
    for path in merge or []:
        cmd.extend(["--merge", str(path)])
    return subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)


def test_parse_text_day_blocks(tmp_path: Path):
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    (raw_dir / "diary.txt").write_text(SAMPLE_RAW.read_text(encoding="utf-8"), encoding="utf-8")
    output = tmp_path / "out.jsonl"

    result = run_parse(raw_dir, output)
    assert result.returncode == 0

    lines = output.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 2
    first = json.loads(lines[0])
    assert "events" in first and "dairy" in first
    assert "FIR 198/26" in first["events"]


def test_merge_sample_jsonl(tmp_path: Path):
    output = tmp_path / "merged.jsonl"
    empty_raw = tmp_path / "empty_raw"
    empty_raw.mkdir()

    result = run_parse(empty_raw, output, merge=[SAMPLE_JSONL])
    assert result.returncode == 0

    lines = output.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) >= 30
