import re
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
GRADLE = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
MANIFEST = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
PRODUCTION = (ROOT / "android/patrolgrid-production.properties").read_text(encoding="utf-8")
POLICY = (ROOT / "docs/PATROLGRID_PRIVACY_POLICY.md").read_text(encoding="utf-8")
RETENTION_SQL = (
    ROOT / "supabase/migrations/202609020007_patrolgrid_retention.sql"
).read_text(encoding="utf-8")


def test_permanent_android_identity_and_unsigned_minified_release():
    assert 'val patrolGridApplicationId = "com.dailybeat.app.patrolgrid"' in GRADLE
    assert 'versionCode = 10_000' in GRADLE
    assert 'versionName = "1.0.0"' in GRADLE
    assert 'applicationIdSuffix = ".qa"' in GRADLE
    assert "isMinifyEnabled = true" in GRADLE
    assert "isShrinkResources = true" in GRADLE
    assert "signingConfigs {" not in GRADLE
    assert "signingConfig =" not in GRADLE
    assert "release.keystore" not in GRADLE


def test_release_commit_is_env_only_exact_full_sha_and_embedded_twice():
    assert 'providers.environmentVariable("PATROLGRID_RELEASE_COMMIT")' in GRADLE
    assert 'providers.gradleProperty("PATROLGRID_RELEASE_COMMIT")' not in GRADLE
    assert 'Regex("[0-9a-f]{40}").matches(releasePatrolGridCommit)' in GRADLE
    assert '"PATROLGRID_RELEASE_COMMIT"' in GRADLE
    assert 'verifySingleStringField("PATROLGRID_RELEASE_COMMIT", releasePatrolGridCommit)' in GRADLE
    assert 'manifestPlaceholders["patrolGridReleaseCommit"] = releasePatrolGridCommit' in GRADLE
    assert 'android:name="com.dailybeat.app.patrolgrid.RELEASE_COMMIT"' in MANIFEST
    assert 'android:value="${patrolGridReleaseCommit}"' in MANIFEST
    assert "PATROLGRID_RELEASE_COMMIT: ${{ github.sha }}" in WORKFLOW


def test_backend_identity_and_anon_digest_are_source_pinned_fail_closed():
    assert 'rootProject.file("patrolgrid-production.properties")' in GRADLE
    assert 'getProperty("SUPABASE_URL", "")' in GRADLE
    assert 'getProperty("SUPABASE_ANON_KEY_SHA256", "")' in GRADLE
    assert "sha256Hex(releaseSupabaseAnonKey) == expectedProductionSupabaseAnonKeySha256" in GRADLE
    assert "releaseSupabaseUrl == expectedProductionSupabaseUrl" in GRADLE
    assert 'expectedProductionSupabaseUrl != "UNCONFIGURED"' in GRADLE
    assert 'providers.environmentVariable("PATROLGRID_BACKEND_IDENTITY")' in GRADLE
    assert 'check(!providers.environmentVariable("PATROLGRID_BACKEND_IDENTITY").isPresent)' in GRADLE
    assert 'providers.gradleProperty("PATROLGRID_BACKEND_IDENTITY")' not in GRADLE
    assert 'verifySingleStringField("PATROLGRID_BACKEND_IDENTITY", expectedProductionSupabaseUrl)' in GRADLE
    assert 'android:name="com.dailybeat.app.patrolgrid.BACKEND_IDENTITY"' in MANIFEST
    assert 'android:value="${patrolGridBackendIdentity}"' in MANIFEST
    configured = {
        key: value
        for line in PRODUCTION.splitlines()
        if line and not line.startswith("#")
        for key, value in [line.split("=", maxsplit=1)]
    }
    assert set(configured) == {
        "PRIVACY_NOTICE_VERSION",
        "PRIVACY_POLICY_STATUS",
        "SUPABASE_ANON_KEY_SHA256",
        "SUPABASE_URL",
    }
    assert configured["PRIVACY_NOTICE_VERSION"] == "3"
    placeholder = (
        configured["SUPABASE_URL"] == "UNCONFIGURED"
        and configured["SUPABASE_ANON_KEY_SHA256"] == "UNCONFIGURED"
        and configured["PRIVACY_POLICY_STATUS"] == "UNAPPROVED"
    )
    parsed = urlsplit(configured["SUPABASE_URL"])
    reviewed = (
        parsed.scheme == "https"
        and bool(parsed.hostname)
        and not parsed.username
        and not parsed.password
        and parsed.path in ("", "/")
        and not parsed.query
        and not parsed.fragment
        and bool(re.fullmatch(r"[0-9a-f]{64}", configured["SUPABASE_ANON_KEY_SHA256"]))
        and configured["PRIVACY_POLICY_STATUS"] == "APPROVED"
    )
    assert placeholder or reviewed
    assert "PATROLGRID_BACKEND_IDENTITY:" not in WORKFLOW


def test_backend_values_are_environment_secrets_but_not_recorded_plaintext():
    assert "SUPABASE_URL: ${{ secrets.PATROLGRID_PRODUCTION_SUPABASE_URL }}" in WORKFLOW
    assert "SUPABASE_ANON_KEY: ${{ secrets.PATROLGRID_PRODUCTION_SUPABASE_ANON_KEY }}" in WORKFLOW
    assert "secrets.SUPABASE_URL" not in WORKFLOW
    assert "secrets.SUPABASE_ANON_KEY" not in WORKFLOW
    assert "supabaseAnonKeySha256" in WORKFLOW
    assert "supabaseUrlSha256" in WORKFLOW
    assert "eyJ" not in GRADLE
    assert "supabase.co" not in GRADLE


def test_release_has_no_cloud_apk_signing_or_symmetric_transfer_material():
    for value in (
        "PATROLGRID_RELEASE_KEYSTORE_BASE64",
        "PATROLGRID_RELEASE_STORE_PASSWORD",
        "PATROLGRID_RELEASE_KEY_PASSWORD",
        "PATROLGRID_ARTIFACT_TRANSFER_KEY",
        "DAILYBEAT_KEYSTORE_BASE64",
        "--symmetric",
        "AES256",
    ):
        assert value not in WORKFLOW
    assert "sign-and-package" not in WORKFLOW
    assert "release/patrolgrid-release-public-key.asc" in WORKFLOW


def test_open_source_map_and_fixed_retention_remain_release_policy():
    map_source = (
        ROOT
        / "android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGeographicRouteMap.kt"
    ).read_text(encoding="utf-8")
    assert 'openFreeMapStyleUrl = "https://tiles.openfreemap.org/styles/liberty"' in GRADLE
    assert 'openFreeMapDarkStyleUrl = "https://tiles.openfreemap.org/styles/fiord"' in GRADLE
    assert 'implementation("org.maplibre.gl:android-sdk:' in GRADLE
    assert "PATROLGRID_MAP_STYLE_URL: ${{ secrets." not in WORKFLOW
    assert "uiSettings.isAttributionEnabled = true" in map_source
    assert "uiSettings.isLogoEnabled = true" in map_source
    assert "val patrolGridRetentionDays = 365" in GRADLE
    assert 'buildConfigField("int", "PATROLGRID_RETENTION_DAYS"' in GRADLE
    assert "PATROLGRID_RETENTION_DAYS: ${{ secrets." not in WORKFLOW


def test_privacy_notice_url_is_commit_pinned_and_not_secret_overridable():
    canonical = (
        "https://github.com/sampathmannam/dailybeat/blob/${{ github.sha }}/"
        "docs/PATROLGRID_PRIVACY_POLICY.md"
    )
    assert f"PATROLGRID_PRIVACY_POLICY_URL: {canonical}" in WORKFLOW
    assert f"EXPECTED_PRIVACY_POLICY_URL: {canonical}" in WORKFLOW
    assert "secrets.PATROLGRID_PRIVACY_POLICY_URL" not in WORKFLOW
    assert "/blob/main/docs/PATROLGRID_PRIVACY_POLICY.md" not in WORKFLOW


def test_privacy_approval_and_notice_version_are_source_pinned_fail_closed():
    assert 'getProperty("PRIVACY_POLICY_STATUS", "")' in GRADLE
    assert 'getProperty("PRIVACY_NOTICE_VERSION", "")' in GRADLE
    assert 'expectedPrivacyPolicyStatus == "APPROVED"' in GRADLE
    assert "expectedPrivacyNoticeVersion == 3" in GRADLE
    for name in ("PATROLGRID_PRIVACY_POLICY_STATUS", "PATROLGRID_PRIVACY_NOTICE_VERSION"):
        assert f'providers.environmentVariable("{name}")' in GRADLE
        assert f'providers.gradleProperty("{name}")' not in GRADLE
        assert f"{name}:" not in WORKFLOW
    assert 'verifySingleStringField("PATROLGRID_PRIVACY_POLICY_STATUS"' in GRADLE
    assert "PATROLGRID_PRIVACY_NOTICE_VERSION" in GRADLE
    assert 'manifestPlaceholders["patrolGridPrivacyPolicyStatus"]' in GRADLE
    assert 'manifestPlaceholders["patrolGridPrivacyNoticeVersion"]' in GRADLE
    assert 'android:name="com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS"' in MANIFEST
    assert 'android:name="com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION"' in MANIFEST
    assert "privacyPolicyStatus" in WORKFLOW
    assert "privacyNoticeVersion" in WORKFLOW
    assert "PRIVACY_POLICY_STATUS=" in PRODUCTION


def test_release_policy_contract_binds_notice_retention_contact_and_emergency_routes():
    for exact_clause in (
        "**Notice version:** 3",
        "**Retention period:** 365 days",
        "There is no separate PatrolGrid technical-support desk.",
        "subdivision supervisor through the existing official Department",
        "## 8. Access, correction, export, deletion, and grievances",
        "To make a privacy request for access",
        "normal chain of command",
        "radio",
        "official telephone",
    ):
        assert exact_clause in POLICY
        assert exact_clause in GRADLE
    assert "interval '8760 hours'" in RETENTION_SQL
    assert "val patrolGridRetentionDays = 365" in GRADLE
    assert 'check("Deployment draft" !in privacyPolicyText)' in GRADLE
