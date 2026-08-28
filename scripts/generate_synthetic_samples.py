"""Generate synthetic IPS diary training samples for bootstrapping fine-tune.

Writes data/samples/diary_train.sample.jsonl (30 rows by default).
These are illustrative — replace with real past diaries before production training.

Usage:
    python scripts/generate_synthetic_samples.py
    python scripts/generate_synthetic_samples.py --count 50
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "data" / "samples" / "diary_train.sample.jsonl"

TEMPLATES = [
    (
        "0945 Office. Reviewed pending FIRs with SI Patel. 1100 Court attendance for bail matter in FIR 198/26. "
        "1430 Market beat patrol with IO Rajan. 1700 Meeting with SP on crime review.",
        "At 0945 hours, attended office and reviewed pending FIRs with SI Patel. At 1100 hours, attended court "
        "for bail hearing in FIR 198/26. At 1430 hours, conducted Market Beat patrol with IO Rajan. "
        "At 1700 hours, attended crime review meeting with SP.",
    ),
    (
        "0815 Received complaint of domestic dispute at Gandhi Nagar. 0900 Visited spot, counselled parties. "
        "1030 Registered DD entry. 1500 Follow-up visit, parties reconciled.",
        "At 0815 hours, received a complaint regarding a domestic dispute at Gandhi Nagar. At 0900 hours, "
        "visited the spot and counselled both parties. At 1030 hours, registered a DD entry. "
        "At 1500 hours, conducted follow-up visit; parties reconciled.",
    ),
    (
        "1140 Market Beat. Met IO Rajan. Inspected chain snatching case FIR 247/26. Collected CCTV leads. "
        "1600 Briefed night duty staff on patrolling.",
        "At 1140 hours, during Market Beat, met IO Rajan and inspected progress in chain snatching case "
        "FIR 247/26. Collected CCTV leads from shopkeepers. At 1600 hours, briefed night duty staff on patrolling.",
    ),
    (
        "0700 Flag march with sector team. 1000 Surprise check at liquor shop. One violation noted, seized stock. "
        "1400 Attended public grievance camp at community hall.",
        "At 0700 hours, conducted flag march with sector team. At 1000 hours, carried out surprise check at a "
        "liquor shop; one violation was noted and stock seized. At 1400 hours, attended public grievance camp "
        "at community hall.",
    ),
    (
        "1015 Road accident on NH-44. Coordinated with ambulance. 1100 Recorded statements. 1300 Traffic diversion "
        "lifted after clearance.",
        "At 1015 hours, attended a road accident on NH-44 and coordinated with ambulance services. At 1100 hours, "
        "recorded statements of witnesses. At 1300 hours, traffic diversion was lifted after scene clearance.",
    ),
    (
        "0900 Intelligence input on suspicious movement. 1030 Joint patrol with CRPF. 1530 Area domination in "
        "sensitive pockets. No untoward incident.",
        "At 0900 hours, received intelligence input regarding suspicious movement. At 1030 hours, conducted joint "
        "patrol with CRPF. At 1530 hours, carried out area domination in sensitive pockets. No untoward incident reported.",
    ),
    (
        "0830 Court for remand of accused in FIR 312/26. 1200 Office — disposed 8 pending applications. "
        "1730 Reviewed beat diary registers.",
        "At 0830 hours, attended court for remand of accused in FIR 312/26. At 1200 hours, in office, disposed "
        "eight pending applications. At 1730 hours, reviewed beat diary registers.",
    ),
    (
        "0645 Morning briefing with staff. 0950 School safety audit at Govt High School. 1410 Theft complaint "
        "at bus stand, registered FIR 089/26.",
        "At 0645 hours, conducted morning briefing with staff. At 0950 hours, carried out school safety audit at "
        "Govt High School. At 1410 hours, attended theft complaint at bus stand and registered FIR 089/26.",
    ),
    (
        "1115 VIP movement coordination at circuit house. 1345 Beat meeting with shopkeepers. 1620 Checked night "
        "patrol deployment chart.",
        "At 1115 hours, coordinated VIP movement arrangements at circuit house. At 1345 hours, held beat meeting "
        "with shopkeepers. At 1620 hours, checked night patrol deployment chart.",
    ),
    (
        "1020 Cyber fraud complaint — mobile wallet scam. 1130 Registered FIR 156/26. 1500 Bank liaison for "
        "transaction trail.",
        "At 1020 hours, received cyber fraud complaint regarding mobile wallet scam. At 1130 hours, registered "
        "FIR 156/26. At 1500 hours, liaised with bank officials for transaction trail.",
    ),
]


def build_samples(count: int) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for i in range(count):
        events, dairy = TEMPLATES[i % len(TEMPLATES)]
        if i >= len(TEMPLATES):
            dairy = dairy + f" (Day {i + 1} routine follow-up noted.)"
        rows.append({"events": events, "dairy": dairy})
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=30)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    rows = build_samples(args.count)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    print(f"Wrote {len(rows)} synthetic samples to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
