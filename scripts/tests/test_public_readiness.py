from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[2]

def test_owner_approved_gpl_licence_is_bundled_and_scope_is_explicit():
    licence = (ROOT / "LICENSE").read_text()
    readme = (ROOT / "README.md").read_text()
    assert "GNU GENERAL PUBLIC LICENSE" in licence
    assert "Version 3, 29 June 2007" in licence
    assert "END OF TERMS AND CONDITIONS" in licence
    assert "GPL-3.0-only" in readme
    assert "Third-party code and assets retain their own licences" in readme
    assert 'license = {file = "LICENSE"}' in (ROOT / "pyproject.toml").read_text()

def test_foss_build_has_its_own_required_dependency_verification():
    gradle = (ROOT / "android/app/build.gradle.kts").read_text()
    assert 'if (!dailybeatFoss) apply(from = "gms-dependencies.gradle.kts")' in gradle
    assert 'play-services-location:21.3.0' in (ROOT / "android/app/gms-dependencies.gradle.kts").read_text()
    assert 'tasks.register("verifyGoogleFreeDependencies")' in gradle
    workflow = yaml.safe_load((ROOT / ".github/workflows/ci.yml").read_text())
    steps = workflow["jobs"]["foss-build"]["steps"]
    assert any("verifyGoogleFreeDependencies -PdailybeatFoss=true" in step.get("run", "") for step in steps)
    for source in (ROOT / "android/app/src/main/java").rglob("*.kt"):
        assert "import com.google.android.gms" not in source.read_text(), source

def test_encrypted_backup_is_separate_from_legacy_storage():
    migration = (ROOT / "supabase/migrations/202609160001_encrypted_backups.sql").read_text().lower()
    assert "create table public.dailybeat_encrypted_backups" in migration
    assert "enable row level security" in migration
    assert "revoke all" in migration
    assert "from public, anon, authenticated" in migration
    for action in ("select", "insert", "update", "delete"):
        assert f"for {action} to authenticated" in migration
    assert "12582912" in migration
    client = (ROOT / "android/app/src/main/java/com/dailybeat/app/backup/SupabaseBackupClient.kt").read_text()
    assert '/rest/v1/dailybeat_encrypted_backups?on_conflict=user_id' in client
    assert 'downloadLegacy()' in client
