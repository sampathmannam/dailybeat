from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GRADLE = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")


def test_release_backend_and_policy_inputs_are_environment_only():
    assert (
        'val releaseSupabaseUrl = supabaseUrlFromEnvironment' in GRADLE
    )
    assert (
        'val releaseSupabaseAnonKey = supabaseAnonKeyFromEnvironment' in GRADLE
    )
    assert (
        "val releasePatrolGridPrivacyPolicyUrl = "
        "patrolGridPrivacyPolicyUrlFromEnvironment"
    ) in GRADLE

    release_block = GRADLE.split("        release {", maxsplit=1)[1].split(
        "            isMinifyEnabled", maxsplit=1
    )[0]
    assert "releaseSupabaseUrl" in release_block
    assert "releaseSupabaseAnonKey" in release_block
    assert "releasePatrolGridPrivacyPolicyUrl" in release_block
    assert 'gradleProperty("' not in release_block


def test_release_rejects_same_name_gradle_properties():
    validation = GRADLE.split(
        "val validateReleaseConfiguration", maxsplit=1
    )[1].split("val verifyReleaseBuildConfigValues", maxsplit=1)[0]

    for name in (
        "SUPABASE_URL",
        "SUPABASE_ANON_KEY",
        "PATROLGRID_PRIVACY_POLICY_URL",
        "PATROLGRID_MAP_STYLE_URL",
        "PATROLGRID_RELEASE_COMMIT",
        "PATROLGRID_BACKEND_IDENTITY",
        "PATROLGRID_PRIVACY_POLICY_STATUS",
        "PATROLGRID_PRIVACY_NOTICE_VERSION",
    ):
        assert f'            "{name}",' in validation
    assert ".filter { providers.gradleProperty(it).isPresent }" in validation
    assert "check(forbiddenGradleProperties.isEmpty())" in validation


def test_release_map_is_source_pinned_while_debug_override_remains_available():
    assert (
        'val openFreeMapStyleUrl = "https://tiles.openfreemap.org/styles/liberty"'
        in GRADLE
    )
    assert "val releasePatrolGridMapStyleUrl = openFreeMapStyleUrl" in GRADLE
    assert 'providers.gradleProperty("PATROLGRID_MAP_STYLE_URL")' in GRADLE
    assert 'providers.environmentVariable("PATROLGRID_MAP_STYLE_URL")' in GRADLE
    assert (
        'check(!providers.environmentVariable("PATROLGRID_MAP_STYLE_URL").isPresent)'
        in GRADLE
    )


def test_release_validation_checks_real_policy_file_without_following_symlinks():
    assert (
        'rootProject.file("../docs/PATROLGRID_PRIVACY_POLICY.md")' in GRADLE
    )
    assert "Files.isRegularFile(privacyPolicyPath, LinkOption.NOFOLLOW_LINKS)" in GRADLE
    assert "!Files.isSymbolicLink(privacyPolicyPath)" in GRADLE
    assert "Files.size(privacyPolicyPath) > 0L" in GRADLE

    policy = ROOT / "docs/PATROLGRID_PRIVACY_POLICY.md"
    assert policy.is_file()
    assert not policy.is_symlink()
    assert policy.stat().st_size > 0


def test_generated_release_build_config_has_an_exact_value_verifier():
    verifier = GRADLE.split(
        "val verifyReleaseBuildConfigValues", maxsplit=1
    )[1].split(
        'tasks.matching { it.name == "preReleaseBuild"', maxsplit=1
    )[0]

    assert 'dependsOn("generateReleaseBuildConfig")' in verifier
    for name in (
        "SUPABASE_URL",
        "SUPABASE_ANON_KEY",
        "PATROLGRID_RELEASE_COMMIT",
        "PATROLGRID_BACKEND_IDENTITY",
        "PATROLGRID_PRIVACY_POLICY_STATUS",
        "PATROLGRID_PRIVACY_POLICY_URL",
        "PATROLGRID_MAP_STYLE_URL",
        "APPLICATION_ID",
    ):
        assert f'verifySingleStringField("{name}"' in verifier
    assert "PATROLGRID_RETENTION_DAYS" in verifier
    assert "PATROLGRID_PRIVACY_NOTICE_VERSION" in verifier
    assert 'it.name == "assembleRelease" || it.name == "bundleRelease"' in GRADLE


def test_gradle_release_is_unsigned_and_cannot_access_permanent_signing_material():
    assert "signingConfigs {" not in GRADLE
    assert "signingConfig =" not in GRADLE
    assert "release.keystore" not in GRADLE
    assert "DAILYBEAT_STORE_PASSWORD" not in GRADLE
    assert "DAILYBEAT_KEY_PASSWORD" not in GRADLE
    assert "System.getenv(" not in GRADLE
    assert "isMinifyEnabled = true" in GRADLE
    assert "isShrinkResources = true" in GRADLE
    assert '"proguard-rules.pro"' in GRADLE


def test_unsigned_release_candidate_has_an_exact_output_verifier():
    verifier = GRADLE.split(
        "val verifyUnsignedReleaseCandidate", maxsplit=1
    )[1].split(
        'tasks.matching { it.name == "preReleaseBuild"', maxsplit=1
    )[0]

    assert 'dependsOn("assembleRelease")' in verifier
    assert '"outputs/apk/release/app-release-unsigned.apk"' in verifier
    assert '"outputs/apk/release/app-release.apk"' in verifier
    assert 'check(!unexpectedlySignedApk.exists())' in verifier
    assert '"outputs/mapping/release/mapping.txt"' in verifier
