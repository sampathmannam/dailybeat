from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


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
    workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    assert "assembleDebug" not in workflow
    assert "app-debug.apk" not in workflow
    assert "DAILYBEAT_KEYSTORE_BASE64" in workflow
    assert "apksigner verify --print-certs" in workflow
    assert "app-release.apk" in workflow


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
