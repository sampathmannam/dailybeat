from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_android_version_advances_for_obtainium_update():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    release_marker = (ROOT / "release/version.txt").read_text(encoding="utf-8").strip()

    assert 'versionCode = 13' in gradle
    assert 'versionName = "3.7.0"' in gradle
    assert release_marker == "3.7.0"


def test_release_build_requires_the_permanent_signing_key():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'signingConfigs.getByName("debug")' not in gradle
    assert 'signingConfig = signingConfigs.getByName("release")' in gradle


def test_release_shrinker_allows_pdfbox_optional_jpeg2000_codec():
    rules = (ROOT / "android/app/proguard-rules.pro").read_text(encoding="utf-8")

    assert "-dontwarn com.gemalto.jp2.**" in rules


def test_debug_build_is_isolated_from_the_installed_release_app():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'applicationIdSuffix = ".qa"' in gradle


def test_phone_gate_targets_hardware_and_reinstalls_after_instrumentation_cleanup():
    runner = (ROOT / "scripts/mac_phone_e2e.sh").read_text(encoding="utf-8")

    assert 'case "$MAC_ADB_SERIAL" in' in runner
    assert "emulator-*)" in runner
    assert 'QA_TEST_PACKAGE="${QA_PACKAGE}.test"' in runner
    assert 'mac_adb uninstall "$disposable_package"' in runner
    assert 'mac_adb uninstall "com.dailybeat.app"' not in runner
    assert runner.index('mac_adb uninstall "$disposable_package"') < runner.index(
        "./gradlew connectedDebugAndroidTest"
    )
    assert runner.index("./gradlew connectedDebugAndroidTest") < runner.index("./gradlew installDebug")
    assert 'pm path "$QA_PACKAGE"' in runner


def test_phone_installer_defaults_to_the_current_signed_stable_release():
    installer = (ROOT / "scripts/mac_install_release_apk.sh").read_text(encoding="utf-8")

    assert 'DAILYBEAT_RELEASE_TAG:-v3.6.0' in installer


def test_release_publishes_only_the_stable_apk_and_verifies_its_certificate():
    workflow = (ROOT / ".github/workflows/publish-release.yml").read_text(encoding="utf-8")

    assert "assembleDebug" not in workflow
    assert "app-debug.apk" not in workflow
    assert "DAILYBEAT_KEYSTORE_BASE64" in workflow
    assert "apksigner verify --print-certs" in workflow
    assert "app-release.apk" in workflow
    assert "release/version.txt" in workflow
    assert "release/requests/*.txt" in workflow
    assert "workflow_dispatch:" in workflow
    assert "build instrumentation patrolgrid-backend codeql" in workflow
    assert 'if existing_tag_sha="$(gh api' in workflow
    assert "2>/dev/null || true" not in workflow


def test_cloud_backup_schema_enforces_owner_only_row_level_security():
    migration = (ROOT / "supabase/migrations/202608310001_dailybeat_backups.sql").read_text(encoding="utf-8")
    normalized = " ".join(migration.lower().split())

    assert "enable row level security" in normalized
    assert "auth.uid() = user_id" in normalized
    assert "for select" in normalized
    assert "for insert" in normalized
    assert "for update" in normalized
    assert "for delete" in normalized


def test_release_injects_public_supabase_configuration_from_github_secrets():
    workflow = (ROOT / ".github/workflows/publish-release.yml").read_text(encoding="utf-8")
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert "SUPABASE_URL: ${{ secrets.SUPABASE_URL }}" in workflow
    assert "SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}" in workflow
    assert "SUPABASE_URL" in gradle
    assert "SUPABASE_ANON_KEY" in gradle
    assert "supabase.co" not in gradle
    assert "eyJ" not in gradle


def test_ci_requires_a_live_cloud_backup_round_trip():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    runner = (ROOT / ".github/scripts/run-instrumentation.sh").read_text(encoding="utf-8")

    assert 'DAILYBEAT_REQUIRE_LIVE_BACKUP: "1"' in workflow
    assert "secrets.DAILYBEAT_BACKUP_TEST_EMAIL" in workflow
    assert "secrets.DAILYBEAT_BACKUP_TEST_PASSWORD" in workflow
    assert "Verify live backup gate configuration" in workflow
    assert 'branches: [main, "hardening/**"]' not in workflow
    assert "backupEmailSha" in runner
    assert "backupPasswordSha" in runner
    assert "backupConfigSha" in runner
    assert "connectedDebugAndroidTest" in runner
