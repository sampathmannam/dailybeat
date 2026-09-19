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


def sample(hour, **changes):
    return {"device_serial": "phone", "phase": "capture_on", "timestamp_utc": f"2026-09-17T{hour:02}:00:00Z",
            "battery_level": str(90-hour), "status": "3", "plugged": "0", **changes}


def test_phase_transitions_are_not_bridged():
    assert MODULE.battery_rates([sample(0), sample(2, phase="capture_off"), sample(4)]) == {}


def test_unknown_power_state_breaks_interval_instead_of_crashing_or_bridging():
    assert MODULE.battery_rates([sample(0), sample(2, plugged=""), sample(4)]) == {}


def test_build_boot_and_backend_changes_break_intervals():
    for key in ("version_name", "build_fingerprint", "package", "boot_id", "capture_backend"):
        assert MODULE.battery_rates([sample(0, **{key:"before"}), sample(2, **{key:"after"})]) == {}


def test_unplugged_full_unknown_and_invalid_values_are_not_discharge_evidence():
    for changes in ({"status":"5"}, {"status":"1"}, {"battery_level":"101"}, {"exclude_interval":"yes"}):
        assert MODULE.battery_rates([sample(0), sample(2, **changes)]) == {}
    assert MODULE.battery_rates([sample(0), sample(20)]) == {}


def test_backends_are_reported_separately():
    assert MODULE.battery_rates([sample(0, capture_backend="standard"), sample(2, capture_backend="standard")]) == {"capture_on/standard":[1.0]}


def test_false_stop_does_not_count_as_reference_stop():
    assert MODULE.recall([{"duration_minutes":"20", "captured":"yes", "false_positive":"yes"}]) == (0,0,1)


SAMPLER_SPEC = importlib.util.spec_from_file_location("sampler", SCRIPT.with_name("capture_battery_sample.py"))
SAMPLER = importlib.util.module_from_spec(SAMPLER_SPEC)
SAMPLER_SPEC.loader.exec_module(SAMPLER)


def battery(usb="false", status="3"):
    return f"Current Battery Service state:\n  AC powered: false\n  USB powered: {usb}\n  Wireless powered: false\n  status: {status}\n  level: 88\n  scale: 100\n  temperature: 290\n"


def test_android_power_booleans_are_read_correctly():
    assert SAMPLER.battery_values(battery())["plugged"] == "0"
    assert SAMPLER.battery_values(battery("true", "2"))["plugged"] == "2"


def test_sampler_rejects_missing_charging_information():
    import pytest
    with pytest.raises(ValueError):
        SAMPLER.battery_values(battery().replace("  USB powered: false\n", ""))


def test_sampler_rejects_emulator_before_collecting_or_writing(tmp_path, monkeypatch):
    import pytest
    monkeypatch.setattr(SAMPLER.subprocess, "check_output", lambda *a, **k: "device\n")
    with pytest.raises(ValueError, match="Emulators"):
        SAMPLER.collect("emulator-5554", "trial", "capture_off", tmp_path, "com.dailybeat.app", "standard")
    assert list(tmp_path.iterdir()) == []
