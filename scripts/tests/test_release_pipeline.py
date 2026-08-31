from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def _load_workflow(name: str) -> dict:
    return yaml.safe_load((ROOT / f".github/workflows/{name}").read_text(encoding="utf-8"))


def _workflow_step(job: dict, *, name: str | None = None, uses: str | None = None) -> dict:
    matches = [
        step
        for step in job["steps"]
        if (name is None or step.get("name") == name)
        and (uses is None or step.get("uses") == uses)
    ]
    assert len(matches) == 1
    return matches[0]


def test_android_version_advances_for_obtainium_update():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'versionCode = 11' in gradle
    assert 'versionName = "3.5.0"' in gradle


def test_release_build_requires_the_permanent_signing_key():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'signingConfigs.getByName("debug")' not in gradle
    assert 'signingConfig = signingConfigs.getByName("release")' in gradle


def test_debug_build_is_isolated_from_the_installed_release_app():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert 'applicationIdSuffix = ".qa"' in gradle


def test_release_publishes_only_the_stable_apk_and_verifies_its_certificate():
    workflow_text = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    workflow = _load_workflow("release.yml")
    release_job = workflow["jobs"]["build-and-release"]
    release_step = _workflow_step(release_job, uses="softprops/action-gh-release@v2")
    release_assets = {
        line.strip()
        for line in release_step["with"]["files"].splitlines()
        if line.strip()
    }

    assert "assembleDebug" not in workflow_text
    assert "app-debug.apk" not in workflow_text
    assert "DAILYBEAT_KEYSTORE_BASE64" in workflow_text
    assert "apksigner verify --print-certs" in workflow_text
    assert release_assets == {
        "android/app/build/outputs/apk/release/app-release.apk",
        "SHA256SUMS.txt",
    }


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
    workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")

    assert "SUPABASE_URL: ${{ secrets.SUPABASE_URL }}" in workflow
    assert "SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}" in workflow
    assert "SUPABASE_URL" in gradle
    assert "SUPABASE_ANON_KEY" in gradle
    assert "supabase.co" not in gradle
    assert "eyJ" not in gradle


def test_today_defers_maplibre_to_dedicated_destination():
    today = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/today/TodayScreen.kt").read_text()
    scaffold = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/DailyBeatAppScaffold.kt").read_text()
    preview = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/components/JourneyRoutePreview.kt").read_text()

    assert "JourneyMapPreview" not in today
    assert "JourneyRoutePreview" in today
    assert 'const val MAP = "journey-map"' in scaffold
    assert "MapLibre" not in preview
    assert "MapView" not in preview


def test_map_presence_semantics_remain_stable():
    map_view = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/components/JourneyMapPreview.kt").read_text()
    navigation_test = (ROOT / "android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt").read_text()

    assert "mapView.contentDescription =" not in map_view
    assert 'testTag("journey_map_ready")' in map_view
    assert navigation_test.count("Until.gone(By.desc(backDescription))") == 2


def test_ci_separates_fast_verification_from_emulator_and_keeps_failure_evidence():
    workflow = _load_workflow("ci.yml")
    navigation_test = (
        ROOT / "android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt"
    ).read_text(encoding="utf-8")
    jobs = workflow["jobs"]

    assert set(jobs) == {"verify", "instrumentation"}
    verify = jobs["verify"]
    instrumentation = jobs["instrumentation"]
    assert "needs" not in verify
    assert "needs" not in instrumentation
    assert instrumentation["timeout-minutes"] == 40

    verify_build = _workflow_step(verify, name="Build and test")["run"]
    verify_python = _workflow_step(verify, name="Python tests")["run"]
    assert "assembleDebug testDebugUnitTest lintDebug" in verify_build
    assert "python3 -m pytest scripts/tests/ -q" in verify_python

    emulator_step = _workflow_step(
        instrumentation,
        uses="reactivecircus/android-emulator-runner@v2",
    )
    assert emulator_step["with"]["api-level"] == 34
    assert emulator_step["with"]["arch"] == "x86_64"
    instrumentation_script = emulator_step["with"]["script"]
    assert instrumentation_script.count("timeout --kill-after=10s 2m bash -c") == 2
    assert (
        "timeout --kill-after=30s 25m ./gradlew connectedDebugAndroidTest"
        in instrumentation_script
    )
    assert instrumentation_script.count("connectedDebugAndroidTest") == 1
    assert 'command_status=$?' in instrumentation_script
    assert "fail_with_evidence()" in instrumentation_script
    assert "set +e" in instrumentation_script
    assert 'exit "$original_status"' in instrumentation_script
    assert (
        'fail_with_evidence "connected_tests" "$command_status"'
        in instrumentation_script
    )
    assert "|| true" not in instrumentation_script
    assert "evidence-status.txt" in instrumentation_script
    assert "adb exec-out screencap -p" in instrumentation_script
    assert "adb logcat -d" in instrumentation_script
    assert "adb pull" in instrumentation_script
    for evidence_label in (
        "connected_reports",
        "raw_results",
        "screenshots",
        "stack_traces",
        "logcat",
    ):
        assert f'"{evidence_label}"' in instrumentation_script

    prepare_step = _workflow_step(
        instrumentation,
        name="Prepare instrumentation failure evidence",
    )
    assert "evidence-status.txt" in prepare_step["run"]
    assert "capture_state=pending" in prepare_step["run"]
    for initial_evidence_state in (
        "initial_connected_reports=not_checked",
        "initial_raw_results=not_checked",
        "initial_screenshots=not_attempted",
        "initial_stack_traces=not_checked",
        "initial_logcat=not_attempted",
    ):
        assert initial_evidence_state in prepare_step["run"]

    upload_step = _workflow_step(instrumentation, uses="actions/upload-artifact@v4")
    artifact_paths = {
        line.strip()
        for line in upload_step["with"]["path"].splitlines()
        if line.strip()
    }
    assert upload_step["if"] == "failure()"
    assert upload_step["with"]["name"] == "instrumentation-failure-evidence"
    assert upload_step["with"]["if-no-files-found"] == "error"
    assert artifact_paths == {
        "android/app/build/reports/androidTests/connected/",
        "android/app/build/outputs/androidTest-results/connected/",
        "android/app/build/outputs/managed_device_android_test_additional_output/",
    }

    assert "stackTraceToString()" in navigation_test
    assert 'File(evidenceDirectory, "${description.methodName}.png")' in navigation_test
