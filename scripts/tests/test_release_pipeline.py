from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def test_android_instrumentation_and_live_backup_are_independent_required_gates():
    workflow = yaml.safe_load(
        (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    )
    jobs = workflow["jobs"]
    assert jobs["offline-instrumentation"]["env"] == {
        "DAILYBEAT_OFFLINE_TESTS_ONLY": "1"
    }
    assert jobs["instrumentation"]["env"] == {"DAILYBEAT_OFFLINE_TESTS_ONLY": "1"}
    assert (
        "secrets.DAILYBEAT_BACKUP_TEST_EMAIL"
        in jobs["live-backup"]["env"]["DAILYBEAT_BACKUP_TEST_EMAIL"]
    )
    assert (
        "secrets.DAILYBEAT_BACKUP_TEST_PASSWORD"
        in jobs["live-backup"]["env"]["DAILYBEAT_BACKUP_TEST_PASSWORD"]
    )
    runner = (ROOT / ".github/scripts/run-instrumentation.sh").read_text(
        encoding="utf-8"
    )
    assert "Offline-only mode cannot bypass the required live backup gate." in runner
    assert "notClass=com.dailybeat.app.CloudBackupLiveTest" in runner


def test_android_version_advances_for_obtainium_update():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    release_marker = (ROOT / "release/version.txt").read_text(encoding="utf-8").strip()

    assert "versionCode = 17" in gradle
    assert 'versionName = "3.8.1"' in gradle
    assert release_marker == "3.8.1"


def test_release_build_requires_the_permanent_signing_key():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'signingConfigs.getByName("debug")' not in gradle
    assert 'signingConfig = signingConfigs.getByName("release")' in gradle


def test_dsr_pdfbox_dependency_is_no_longer_bundled_in_dailybeat():
    rules = (ROOT / "android/app/proguard-rules.pro").read_text(encoding="utf-8")

    assert "com.gemalto.jp2" not in rules
    dependencies = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    assert "pdfbox-android" not in dependencies


def test_debug_build_is_isolated_from_the_installed_release_app():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'providers.gradleProperty("dailybeatDebugApplicationIdSuffix")' in gradle
    assert '.getOrElse(".qa")' in gradle
    assert "applicationIdSuffix = debugApplicationIdSuffix" in gradle
    assert "production data must stay isolated" in gradle
    assert 'tasks.register("verifyDisposableTestTarget")' in gradle
    assert "dependsOn(verifyDisposableTestTarget)" in gradle


def test_phone_gate_targets_hardware_and_reinstalls_after_instrumentation_cleanup():
    runner = (ROOT / "scripts/mac_phone_e2e.sh").read_text(encoding="utf-8")

    assert 'case "$MAC_ADB_SERIAL" in' in runner
    assert "emulator-*)" in runner
    assert 'QA_TEST_PACKAGE="${QA_PACKAGE}.test"' in runner
    assert 'QA_PACKAGE="com.dailybeat.app.qa.e2eloop"' in runner
    assert runner.count("-PdailybeatDebugApplicationIdSuffix=.qa.e2eloop") == 3
    assert 'mac_adb uninstall "$disposable_package"' in runner
    assert 'mac_adb uninstall "com.dailybeat.app"' not in runner
    assert runner.index('mac_adb uninstall "$disposable_package"') < runner.index(
        "./gradlew connectedDebugAndroidTest"
    )
    assert runner.index("./gradlew connectedDebugAndroidTest") < runner.index(
        "./gradlew installDebug"
    )
    assert 'pm path "$QA_PACKAGE"' in runner


def test_phone_gate_does_not_expand_an_empty_array_under_macos_bash_strict_mode():
    runner = (ROOT / "scripts/mac_phone_e2e.sh").read_text(encoding="utf-8")

    assert "phone_instrumentation_args=()" not in runner
    assert "run_phone_instrumentation()" in runner
    assert (
        './gradlew connectedDebugAndroidTest -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop "$@" --no-daemon --stacktrace'
        in runner
    )
    assert "else\n  run_phone_instrumentation\nfi" in runner


def test_mac_helpers_detect_android_studios_bundled_java_runtime():
    helper = (ROOT / "scripts/mac_adb_common.sh").read_text(encoding="utf-8")

    assert '"/Applications/Android Studio.app/Contents/jbr/Contents/Home"' in helper


def test_phone_installer_defaults_to_the_current_signed_stable_release():
    installer = (ROOT / "scripts/mac_install_release_apk.sh").read_text(
        encoding="utf-8"
    )

    assert "DAILYBEAT_RELEASE_TAG:-v3.8.1" in installer


def test_maestro_flow_can_only_clear_the_disposable_qa_app():
    flow = (ROOT / "maestro/onboarding_and_nav.yaml").read_text(encoding="utf-8")

    assert "appId: com.dailybeat.app.qa.e2eloop" in flow
    assert "appId: com.dailybeat.app\n" not in flow


def test_release_publishes_only_the_stable_apk_and_verifies_its_certificate():
    workflow = (ROOT / ".github/workflows/publish-release.yml").read_text(
        encoding="utf-8"
    )

    assert "assembleDebug" not in workflow
    assert "app-debug.apk" not in workflow
    assert "DAILYBEAT_KEYSTORE_BASE64" in workflow
    assert "apksigner verify --print-certs" in workflow
    assert "app-release.apk" in workflow
    assert "release/version.txt" in workflow
    assert "release/requests/*.txt" in workflow
    assert "workflow_dispatch:" in workflow
    assert "build instrumentation live-backup patrolgrid-backend codeql" in workflow
    assert 'if existing_tag_sha="$(gh api' in workflow
    assert "2>/dev/null || true" not in workflow


def test_cloud_backup_schema_enforces_owner_only_row_level_security():
    migration = (
        ROOT / "supabase/migrations/202608310001_dailybeat_backups.sql"
    ).read_text(encoding="utf-8")
    normalized = " ".join(migration.lower().split())

    assert "enable row level security" in normalized
    assert "auth.uid() = user_id" in normalized
    assert "for select" in normalized
    assert "for insert" in normalized
    assert "for update" in normalized
    assert "for delete" in normalized


def test_release_injects_public_supabase_configuration_from_github_secrets():
    workflow = (ROOT / ".github/workflows/publish-release.yml").read_text(
        encoding="utf-8"
    )
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert "SUPABASE_URL: ${{ secrets.SUPABASE_URL }}" in workflow
    assert "SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}" in workflow
    assert "SUPABASE_URL" in gradle
    assert "SUPABASE_ANON_KEY" in gradle
    assert "supabase.co" not in gradle
    assert "eyJ" not in gradle


def test_capture_gaps_are_handled_without_user_review_prompts():
    strings = (ROOT / "android/app/src/main/res/values/strings.xml").read_text(
        encoding="utf-8"
    )
    today = (
        ROOT / "android/app/src/main/java/com/dailybeat/app/ui/today/TodayScreen.kt"
    ).read_text(encoding="utf-8")
    feed = (
        ROOT / "android/app/src/main/java/com/dailybeat/app/ui/feed/FeedScreen.kt"
    ).read_text(encoding="utf-8")
    review = (
        ROOT / "android/app/src/main/java/com/dailybeat/app/ui/review/ReviewDayScreen.kt"
    ).read_text(encoding="utf-8")
    insights = (
        ROOT / "android/app/src/main/java/com/dailybeat/app/ui/insights/InsightsViewModel.kt"
    ).read_text(encoding="utf-8")

    assert "capture_gap_notice" not in strings + today
    assert "day_gap_summary" not in strings + feed
    assert "review_gap_detail" not in strings + review
    assert "Protect route continuity" not in insights


def test_ci_requires_a_live_cloud_backup_round_trip():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    runner = (ROOT / "scripts/live_backup_e2e.py").read_text(encoding="utf-8")

    assert "live-backup:" in workflow
    assert "secrets.DAILYBEAT_BACKUP_TEST_EMAIL" in workflow
    assert "secrets.DAILYBEAT_BACKUP_TEST_PASSWORD" in workflow
    assert "python3 scripts/live_backup_e2e.py" in workflow
    assert 'branches: [main, "hardening/**"]' not in workflow
    assert "/auth/v1/token?grant_type=password" in runner
    assert "/rest/v1/dailybeat_backups?on_conflict=user_id" in runner
    assert "client.upload(session, original)" in runner
    assert "client.delete(session)" in runner
    assert "if client.download(session) != original" in runner
