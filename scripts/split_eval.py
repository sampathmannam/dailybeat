"""Hold out rows from diary_train.jsonl into diary_eval.jsonl (Phase 2).

Usage:
    python scripts/split_eval.py
    python scripts/split_eval.py --holdout 10 --seed 42
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TRAIN = ROOT / "data" / "diary_train.jsonl"
DEFAULT_EVAL = ROOT / "data" / "diary_eval.jsonl"


def read_jsonl(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        obj = json.loads(stripped)
        rows.append({"events": str(obj["events"]).strip(), "dairy": str(obj["dairy"]).strip()})
    return rows


def write_jsonl(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Split held-out eval set from training JSONL.")
    parser.add_argument("--train", type=Path, default=DEFAULT_TRAIN)
    parser.add_argument("--eval", type=Path, default=DEFAULT_EVAL)
    parser.add_argument("--holdout", type=int, default=10)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--min-eval", type=int, default=10)
    args = parser.parse_args()

    if not args.train.is_file():
        print(f"Missing train file: {args.train}")
        print("Run: python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl")
        return 1

    rows = read_jsonl(args.train)
    if len(rows) <= args.holdout:
        print(f"Need more than {args.holdout} training rows; found {len(rows)}")
        return 1

    rng = random.Random(args.seed)
    indices = list(range(len(rows)))
    rng.shuffle(indices)
    eval_indices = set(indices[:args.holdout])
    eval_rows = [rows[i] for i in sorted(eval_indices)]
    train_rows = [row for i, row in enumerate(rows) if i not in eval_indices]

    write_jsonl(args.eval, eval_rows)
    write_jsonl(args.train, train_rows)

    print(f"Wrote {len(eval_rows)} eval rows to {args.eval}")
    print(f"Updated train with {len(train_rows)} rows at {args.train}")
    if len(eval_rows) < args.min_eval:
        print(f"WARNING: eval rows {len(eval_rows)} < {args.min_eval}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
