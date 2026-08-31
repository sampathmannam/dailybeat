import json
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path
from types import SimpleNamespace
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import yaml

ROOT = Path(__file__).resolve().parents[2]


def _post_mock(server: ThreadingHTTPServer, scenario: str) -> tuple[int, dict]:
    payload = json.dumps(
        {
            "model": "deepseek-chat",
            "temperature": 0.2,
            "messages": [
                {"role": "system", "content": "Synthetic system instruction."},
                {"role": "user", "content": "Synthetic visit [V1] and event [E1]."},
            ],
        }
    ).encode()
    request = Request(
        f"http://127.0.0.1:{server.server_port}/v1/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer synthetic-test-key",
            "Content-Type": "application/json",
            "X-DailyBeat-Scenario": scenario,
        },
        method="POST",
    )
    try:
        response = urlopen(request, timeout=2)
    except HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read())


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


def test_mock_deepseek_serves_every_deterministic_scenario_offline():
    from tools.locust.mock_deepseek import DeepSeekCompatibleHandler

    server = ThreadingHTTPServer(("127.0.0.1", 0), DeepSeekCompatibleHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        results = {
            scenario: _post_mock(server, scenario)
            for scenario in (
                "valid",
                "invalid-citations",
                "empty",
                "rate-limit",
                "server-error",
            )
        }
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    assert results == {
        "valid": (
            200,
            {"choices": [{"message": {"content": "Synthetic daily report [V1] [E1]."}}]},
        ),
        "invalid-citations": (
            200,
            {"choices": [{"message": {"content": "Synthetic daily report [V99]."}}]},
        ),
        "empty": (200, {"choices": [{"message": {"content": ""}}]}),
        "rate-limit": (
            429,
            {"error": {"type": "rate_limit_error", "message": "Synthetic rate limit."}},
        ),
        "server-error": (
            500,
            {"error": {"type": "server_error", "message": "Synthetic server error."}},
        ),
    }


def test_mock_deepseek_rejects_unknown_scenario():
    from tools.locust.mock_deepseek import DeepSeekCompatibleHandler

    server = ThreadingHTTPServer(("127.0.0.1", 0), DeepSeekCompatibleHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        status, body = _post_mock(server, "not-a-scenario")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    assert status == 400
    assert body == {
        "error": {"type": "invalid_scenario", "message": "Unknown DailyBeat scenario."}
    }


def test_locust_profile_accepts_only_expected_status_and_body_shapes():
    from tools.locust.locustfile import SCENARIO_WEIGHTS, validate_response

    assert SCENARIO_WEIGHTS == {"valid": 8, "rate-limit": 1, "server-error": 1}
    assert validate_response(
        "valid",
        200,
        {"choices": [{"message": {"content": "Synthetic daily report [V1] [E1]."}}]},
    ) is None
    assert validate_response(
        "rate-limit",
        429,
        {"error": {"type": "rate_limit_error", "message": "Synthetic rate limit."}},
    ) is None
    assert validate_response(
        "server-error",
        500,
        {"error": {"type": "server_error", "message": "Synthetic server error."}},
    ) is None

    assert validate_response("valid", 500, {}) == "valid: expected HTTP 200, got 500"
    assert validate_response("rate-limit", 429, {}) == (
        "rate-limit: expected error type rate_limit_error"
    )
    assert validate_response("server-error", 500, {"error": {"type": "wrong"}}) == (
        "server-error: expected error type server_error"
    )


def test_locust_performance_budget_requires_volume_zero_failures_and_low_latency():
    from tools.locust.locustfile import performance_budget_violations

    passing = SimpleNamespace(
        total=SimpleNamespace(
            num_requests=100,
            fail_ratio=0.0,
            get_response_time_percentile=lambda percentile: {
                0.95: 250,
                0.99: 500,
            }[percentile],
        )
    )
    assert performance_budget_violations(passing) == []

    failing = SimpleNamespace(
        total=SimpleNamespace(
            num_requests=99,
            fail_ratio=0.01,
            get_response_time_percentile=lambda percentile: {
                0.95: 251,
                0.99: 501,
            }[percentile],
        )
    )
    assert performance_budget_violations(failing) == [
        "request count 99 is below 100",
        "unexpected failure ratio 1.000% exceeds 0.000%",
        "p95 response time 251 ms exceeds 250 ms",
        "p99 response time 501 ms exceeds 500 ms",
    ]


def test_release_runbook_documents_local_load_gate_and_nondestructive_rollback():
    runbook = (ROOT / "docs/RELEASE_RUNBOOK.md").read_text(encoding="utf-8")

    assert "http://127.0.0.1:8765" in runbook
    assert "tools/locust/mock_deepseek.py --port 8765" in runbook
    assert "--headless -u 20 -r 5 -t 60s" in runbook
    assert "zero unexpected failures" in runbook
    assert "p95 <= 250 ms" in runbook
    assert "p99 <= 500 ms" in runbook
    assert "v3.5.0" in runbook
    assert "last-known-good" in runbook
    assert "delete" in runbook.lower()
    assert "apksigner verify --print-certs" in runbook
    assert "Get-FileHash" in runbook
    assert "adb install" in runbook
