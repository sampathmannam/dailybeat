from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "qa" / "run_overnight_phone.ps1"


def test_overnight_runner_isolates_qa_and_preserves_installed_release_data():
    script = RUNNER.read_text(encoding="utf-8")

    assert '"com.dailybeat.app.qa.e2eloop"' in script
    assert '"com.dailybeat.app.qa.e2eloop.test"' in script
    assert "-PdailybeatDebugApplicationIdSuffix=.qa.e2eloop" in script
    assert "$env:ANDROID_SERIAL = $Serial" in script
    assert "if ($LASTEXITCODE -ne 0)" in script
    assert "com.dailybeat.app'" not in script.replace("com.dailybeat.app.qa", "")
    assert "pm clear" not in script
    assert "uninstall" not in script
    assert "FATAL EXCEPTION|ANR in" in script
    assert "No cleanup was executed." in script
