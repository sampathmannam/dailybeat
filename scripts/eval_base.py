"""Evaluate base Qwen2.5-1.5B on held-out diary eval set via Ollama (Phase 3).

Requires Ollama running with qwen2.5:1.5b-instruct pulled.

Usage:
    python scripts/eval_base.py
    python scripts/eval_base.py --model qwen2.5:1.5b-instruct --ollama-url http://127.0.0.1:11434
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_EVAL = ROOT / "data" / "diary_eval.jsonl"
DEFAULT_OUTPUT = ROOT / "data" / "eval_base_outputs.jsonl"
DEFAULT_RESULTS = ROOT / "data" / "eval_results.json"

PROMPT_TEMPLATE = """You are an Indian Police Service officer writing your official daily diary.
Convert the following raw events from the day into a formal dairy entry
in standard IPS dairy format. Use only the information given. Do not invent
details. Use present tense for completed actions. Keep it concise.

EVENTS:
{events}

DAIRY:
"""


def read_jsonl(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        obj = json.loads(stripped)
        rows.append({"events": str(obj["events"]).strip(), "dairy": str(obj["dairy"]).strip()})
    return rows


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def ollama_generate(client: httpx.Client, model: str, prompt: str) -> str:
    response = client.post(
        "/api/generate",
        json={"model": model, "prompt": prompt, "stream": False},
        timeout=300.0,
    )
    response.raise_for_status()
    data = response.json()
    return str(data.get("response", "")).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Run base-model dairy eval via Ollama.")
    parser.add_argument("--eval", type=Path, default=DEFAULT_EVAL)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--model", default="qwen2.5:1.5b-instruct")
    parser.add_argument("--ollama-url", default="http://127.0.0.1:11434")
    args = parser.parse_args()

    if not args.eval.is_file():
        print(f"Missing eval file: {args.eval}")
        print("Run: python scripts/split_eval.py")
        return 1

    eval_rows = read_jsonl(args.eval)
    outputs: list[dict] = []

    with httpx.Client(base_url=args.ollama_url) as client:
        for idx, row in enumerate(eval_rows, start=1):
            prompt = PROMPT_TEMPLATE.format(events=row["events"])
            print(f"Generating {idx}/{len(eval_rows)}...")
            generated = ollama_generate(client, args.model, prompt)
            outputs.append(
                {
                    "events": row["events"],
                    "target_dairy": row["dairy"],
                    "generated_dairy": generated,
                }
            )

    write_jsonl(args.output, outputs)

    results = {
        "phase": "base",
        "model": args.model,
        "total": len(outputs),
        "passes": None,
        "pass_rate": None,
        "note": "Review eval_base_outputs.jsonl manually; set passes and pass_rate after grading.",
    }
    args.results.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")

    print(f"Wrote {len(outputs)} outputs to {args.output}")
    print(f"Wrote results stub to {args.results}")
    print("Review outputs side-by-side and update eval_results.json with pass counts.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
