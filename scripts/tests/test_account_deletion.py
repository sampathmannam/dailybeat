from pathlib import Path


ROOT = Path(__file__).parents[2]


def test_account_deletion_is_server_only_and_requires_recent_verified_user():
    function = (ROOT / "supabase/functions/delete-account/index.ts").read_text()
    config = (ROOT / "supabase/config.toml").read_text()
    android = "\n".join(
        path.read_text(errors="ignore")
        for path in (ROOT / "android/app/src/main").rglob("*")
        if path.is_file() and path.suffix in {".kt", ".xml", ".txt"}
    )

    assert 'Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")' in function
    assert ".auth.getUser(accessToken)" in function
    assert "createDeleteAccountHandler" in function
    handler = (ROOT / "supabase/functions/delete-account/handler.ts").read_text()
    assert "hasRecentPasswordAuthentication(token, userId" in handler
    assert ".auth.admin.deleteUser(userId, false)" in function
    assert '[functions.delete-account]' in config
    assert "verify_jwt = true" in config
    assert "SUPABASE_SERVICE_ROLE_KEY" not in android


def test_client_reauthenticates_before_calling_account_deletion():
    view_model = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsViewModel.kt").read_text()
    reauth = view_model.index("app.backupCoordinator.signIn(email, password)")
    deletion = view_model.index("app.backupCoordinator.deleteAccount()")
    assert reauth < deletion


def test_packaged_legal_surface_contains_project_and_dependency_licences():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text()
    activity = (ROOT / "android/app/src/main/java/com/dailybeat/app/LegalNoticesActivity.kt").read_text()
    assert 'from(rootProject.file("../LICENSE"))' in gradle
    assert "R.raw.gpl_3_0" in activity
    assert "R.raw.apache_license_2_0" in activity
    assert "R.raw.third_party_notices" in activity
