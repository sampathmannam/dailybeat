from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "qa" / "run_overnight_phone.ps1"


def test_overnight_runner_isolates_qa_and_preserves_installed_release_data():
    script = RUNNER.read_text(encoding="utf-8")

    assert "com.dailybeat.app.patrolgrid.qa" in script
    assert "com.dailybeat.app.patrolgrid.qa.test" in script
    assert "com.dailybeat.app'" not in script.replace("com.dailybeat.app.patrolgrid.qa", "")
    assert r'OK \(\d+ tests?\)' in script
    assert "pm clear" not in script
    assert "uninstall" not in script
    assert "FATAL EXCEPTION|ANR in" in script
    assert "No cleanup was executed." in script
