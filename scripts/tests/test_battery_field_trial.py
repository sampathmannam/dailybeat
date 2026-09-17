import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "analyze_battery_field_trial.py"
SPEC = importlib.util.spec_from_file_location("battery_trial", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def test_battery_rates_exclude_charging_and_short_intervals():
    rows = [
        {"device_serial": "phone", "phase": "capture_on", "timestamp_utc": "2026-09-17T00:00:00Z", "battery_level": "90", "status": "3", "plugged": "0"},
        {"device_serial": "phone", "phase": "capture_on", "timestamp_utc": "2026-09-17T02:00:00Z", "battery_level": "86", "status": "3", "plugged": "0"},
        {"device_serial": "phone", "phase": "capture_on", "timestamp_utc": "2026-09-17T03:00:00Z", "battery_level": "88", "status": "2", "plugged": "1"},
    ]
    assert MODULE.battery_rates(rows) == {"capture_on": [2.0]}


def test_recall_only_scores_stops_of_at_least_ten_minutes():
    rows = [
        {"duration_minutes": "15", "captured": "yes", "false_positive": "no"},
        {"duration_minutes": "25", "captured": "no", "false_positive": "no"},
        {"duration_minutes": "5", "captured": "no", "false_positive": "no"},
        {"duration_minutes": "0", "captured": "no", "false_positive": "yes"},
    ]
    assert MODULE.recall(rows) == (1, 2, 1)
