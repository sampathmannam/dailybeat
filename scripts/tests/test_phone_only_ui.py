from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_navigation_remains_phone_native_at_every_width():
    scaffold = (
        ROOT
        / "android/app/src/main/java/com/dailybeat/app/ui/DailyBeatAppScaffold.kt"
    ).read_text(encoding="utf-8")

    assert "DailyBeatNavigationBar(" in scaffold
    assert "NavigationRail" not in scaffold
    assert "useNavigationRail" not in scaffold
    assert "maxWidth >= 600.dp" not in scaffold


def test_product_contract_is_phone_only():
    product = (ROOT / "PRODUCT.md").read_text(encoding="utf-8")
    design = (ROOT / "docs/DESIGN.md").read_text(encoding="utf-8")

    assert "phone-only" in product.lower()
    assert "phone-only" in design.lower()
    assert "navigation rail" not in design.lower()

