#!/usr/bin/env python3
"""Summarize DailyBeat battery intervals and independently noted stop recall."""

from __future__ import annotations

import argparse
import csv
import statistics
from collections import defaultdict
from datetime import datetime
from pathlib import Path


def parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def battery_rates(rows: list[dict[str, str]]) -> dict[str, list[float]]:
    grouped: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[(row["device_serial"], row["phase"])].append(row)
    rates: dict[str, list[float]] = defaultdict(list)
    for (_, phase), samples in grouped.items():
        samples.sort(key=lambda row: parse_time(row["timestamp_utc"]))
        for before, after in zip(samples, samples[1:]):
            # Android status 2 is charging and plugged > 0 means external power. Mixed intervals
            # cannot support a drain comparison, so exclude them rather than inventing a rate.
            if any(int(row["status"]) == 2 or int(row["plugged"]) > 0 for row in (before, after)):
                continue
            hours = (parse_time(after["timestamp_utc"]) - parse_time(before["timestamp_utc"])).total_seconds() / 3600
            drop = int(before["battery_level"]) - int(after["battery_level"])
            if hours >= 1 and drop >= 0:
                rates[phase].append(drop / hours)
    return dict(rates)


def recall(reference_rows: list[dict[str, str]]) -> tuple[int, int, int]:
    meaningful = [row for row in reference_rows if float(row["duration_minutes"]) >= 10]
    found = sum(row["captured"].strip().lower() == "yes" for row in meaningful)
    false_positives = sum(row["false_positive"].strip().lower() == "yes" for row in reference_rows)
    return found, len(meaningful), false_positives


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("trial_dir", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    battery_file = args.trial_dir / "battery-samples.csv"
    reference_file = args.trial_dir / "stop-reference.csv"
    with battery_file.open(newline="", encoding="utf-8") as handle:
        rates = battery_rates(list(csv.DictReader(handle)))
    lines = ["# DailyBeat physical field-trial summary", ""]
    for phase in ("capture_off", "capture_on"):
        values = rates.get(phase, [])
        lines.append(
            f"- {phase}: {len(values)} valid discharging intervals; "
            + (f"median {statistics.median(values):.2f} battery percentage points/hour." if values else "no valid rate yet.")
        )
    if reference_file.exists():
        with reference_file.open(newline="", encoding="utf-8") as handle:
            found, total, false_positives = recall(list(csv.DictReader(handle)))
        percentage = (100 * found / total) if total else 0
        lines.append(f"- Meaningful-stop recall: {found}/{total} ({percentage:.1f}%); false positives: {false_positives}.")
    lines.extend([
        "",
        "Battery percentage is a coarse device signal, not an energy meter. Compare matched devices, routes, usage, signal conditions, and screen time; retain raw batterystats for review.",
    ])
    report = "\n".join(lines) + "\n"
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
