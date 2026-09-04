"""Tests for split_eval.py"""

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPLIT_SCRIPT = ROOT / "scripts" / "split_eval.py"


def test_split_eval_holdout(tmp_path: Path):
    train = tmp_path / "train.jsonl"
    eval_out = tmp_path / "eval.jsonl"
    rows = [{"events": f"event {i}", "dairy": f"dairy {i}"} for i in range(20)]
    train.write_text("\n".join(json.dumps(r) for r in rows) + "\n", encoding="utf-8")

    cmd = [
        sys.executable,
        str(SPLIT_SCRIPT),
        "--train",
        str(train),
        "--eval",
        str(eval_out),
        "--holdout",
        "10",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT, check=False)
    assert result.returncode == 0

    eval_rows = [json.loads(line) for line in eval_out.read_text(encoding="utf-8").splitlines()]
    train_rows = [json.loads(line) for line in train.read_text(encoding="utf-8").splitlines()]
    assert len(eval_rows) == 10
    assert len(train_rows) == 10
