from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
THEME = (
    ROOT / "android/app/src/main/java/com/dailybeat/app/ui/theme/Theme.kt"
).read_text(encoding="utf-8")
SCAFFOLD = (
    ROOT
    / "android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt"
).read_text(encoding="utf-8")


def test_patrolgrid_follows_the_device_appearance_instead_of_forcing_a_role_theme():
    assert "darkTheme: Boolean = isSystemInDarkTheme()" in THEME
    assert "DailyBeatTheme {" in SCAFFOLD
    assert "state.role == PatrolRole.PATROL" not in SCAFFOLD
