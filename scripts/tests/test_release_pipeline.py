from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_android_version_advances_for_obtainium_update():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'versionCode = 9' in gradle
    assert 'versionName = "3.3.1"' in gradle


def test_release_build_requires_the_permanent_signing_key():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'signingConfigs.getByName("debug")' not in gradle
    assert 'signingConfig = signingConfigs.getByName("release")' in gradle


def test_release_publishes_only_the_stable_apk_and_verifies_its_certificate():
    workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    assert "assembleDebug" not in workflow
    assert "app-debug.apk" not in workflow
    assert "DAILYBEAT_KEYSTORE_BASE64" in workflow
    assert "apksigner verify --print-certs" in workflow
    assert "app-release.apk" in workflow
