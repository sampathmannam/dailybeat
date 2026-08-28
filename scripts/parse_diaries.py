"""Parse raw diary files into diary_train.jsonl for QLoRA fine-tuning.

Input layout (put files in data/raw/):
  - Plain text with day blocks delimited by date headers
  - Each day block contains EVENTS and DAIRY sections

Example day block:
    === 2026-01-15 ===
    EVENTS:
    1140 Market beat, met IO Rajan, chain snatching FIR 247/26
    DAIRY:
    At 1140 hours, proceeded to Market Beat and met IO Rajan regarding...

Also accepts pre-formatted JSONL (one {"events","dairy"} object per line).

Usage:
    python scripts/parse_diaries.py
    python scripts/parse_diaries.py --input data/raw --output data/diary_train.jsonl
    python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = ROOT / "data" / "raw"
DEFAULT_OUTPUT = ROOT / "data" / "diary_train.jsonl"

DATE_HEADER = re.compile(
    r"^={3,}\s*(\d{4}-\d{2}-\d{2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\s*={3,}\s*$",
    re.MULTILINE,
)
ALT_DATE_HEADER = re.compile(
    r"^(?:Date|DATE|Dated)\s*[:.]?\s*(\d{4}-\d{2}-\d{2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\s*$",
    re.MULTILINE,
)
SECTION_EVENTS = re.compile(r"^EVENTS?\s*:\s*$", re.IGNORECASE | re.MULTILINE)
SECTION_DAIRY = re.compile(r"^DAIR(Y|IES)\s*:\s*$", re.IGNORECASE | re.MULTILINE)

TEXT_SUFFIXES = {".txt", ".md", ".text"}
JSONL_SUFFIXES = {".jsonl"}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_jsonl_file(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line_no, line in enumerate(read_text(path).splitlines(), start=1):
        stripped = line.strip()
        if not stripped:
            continue
        try:
            obj = json.loads(stripped)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{line_no}: invalid JSONL: {exc}") from exc
        events = str(obj.get("events", "")).strip()
        dairy = str(obj.get("dairy", "")).strip()
        if events and dairy:
            rows.append({"events": events, "dairy": dairy})
    return rows


def split_day_blocks(text: str) -> list[str]:
    markers = list(DATE_HEADER.finditer(text)) + list(ALT_DATE_HEADER.finditer(text))
    if not markers:
        return [text.strip()] if text.strip() else []

    markers.sort(key=lambda m: m.start())
    blocks: list[str] = []
    for idx, match in enumerate(markers):
        start = match.end()
        end = markers[idx + 1].start() if idx + 1 < len(markers) else len(text)
        block = text[start:end].strip()
        if block:
            blocks.append(block)
    return blocks


def extract_sections(block: str) -> tuple[str, str] | None:
    events_match = SECTION_EVENTS.search(block)
    dairy_match = SECTION_DAIRY.search(block)

    if events_match and dairy_match:
        if events_match.start() < dairy_match.start():
            events = block[events_match.end():dairy_match.start()].strip()
            dairy = block[dairy_match.end():].strip()
        else:
            dairy = block[dairy_match.end():events_match.start()].strip()
            events = block[events_match.end():].strip()
    else:
        parts = re.split(r"\n-{3,}\n", block, maxsplit=1)
        if len(parts) == 2:
            events, dairy = parts[0].strip(), parts[1].strip()
        else:
            return None

    if not events or not dairy:
        return None
    return events, dairy


def parse_text_file(path: Path) -> list[dict[str, str]]:
    text = read_text(path)
    rows: list[dict[str, str]] = []
    for block in split_day_blocks(text):
        parsed = extract_sections(block)
        if parsed:
            events, dairy = parsed
            rows.append({"events": events, "dairy": dairy})
    return rows


def collect_from_directory(input_dir: Path) -> list[dict[str, str]]:
    if not input_dir.is_dir():
        return []

    rows: list[dict[str, str]] = []
    for path in sorted(input_dir.rglob("*")):
        if not path.is_file():
            continue
        suffix = path.suffix.lower()
        if suffix in JSONL_SUFFIXES:
            rows.extend(parse_jsonl_file(path))
        elif suffix in TEXT_SUFFIXES:
            rows.extend(parse_text_file(path))
    return rows


def load_existing_jsonl(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        return []
    return parse_jsonl_file(path)


def write_jsonl(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def dedupe(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    seen: set[tuple[str, str]] = set()
    unique: list[dict[str, str]] = []
    for row in rows:
        key = (row["events"], row["dairy"])
        if key not in seen:
            seen.add(key)
            unique.append(row)
    return unique


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build diary_train.jsonl from raw diary files.")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT, help="Directory of raw diary files")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Output JSONL path")
    parser.add_argument(
        "--merge",
        type=Path,
        nargs="*",
        default=[],
        help="Additional JSONL files to merge (e.g. synthetic samples)",
    )
    parser.add_argument("--min-rows", type=int, default=30, help="Minimum rows required to pass verification")
    args = parser.parse_args(argv)

    rows = collect_from_directory(args.input)
    for merge_path in args.merge:
        rows.extend(load_existing_jsonl(merge_path))

    rows = dedupe(rows)
    write_jsonl(args.output, rows)

    print(f"Wrote {len(rows)} rows to {args.output}")
    if rows:
        print("First sample:")
        print(json.dumps(rows[0], ensure_ascii=False, indent=2))

    if len(rows) < args.min_rows:
        print(
            f"WARNING: {len(rows)} rows < {args.min_rows} required. "
            "Add raw files to data/raw/ or merge data/samples/diary_train.sample.jsonl",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
