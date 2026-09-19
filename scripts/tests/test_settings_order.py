"""Guards Settings' privacy-first, progressively disclosed information architecture.

The Settings index must lead with capture/places and privacy/data, then journal/appearance and
connected services. The detailed controls stay behind those focused entry points instead of
returning to one continuous page.

That ordering is not cosmetic. Named places is where a private zone is declared, and a private
zone is the mechanism that keeps a home address out of every cloud report, geocoder call and
shared export. Burying it under a base-URL field buries the app's privacy contract.

Order and progressive disclosure are layout properties, so this guard reads the source while
instrumentation verifies the real Compose interaction and Android Back behavior.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SETTINGS_SCREEN = (
    ROOT / "android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsScreen.kt"
)

# The string resource that names each group, in the order the officer must meet them.
EXPECTED_ORDER = [
    "settings_capture_group",
    "places_title",
    # Retention, local erasure and legal notices are core privacy controls, before cosmetics.
    "settings_data_privacy_group",
    "settings_appearance_group",
    # The group contains both officer and supervisor fields, so its heading describes the pair.
    "settings_identity_group",
    "settings_backup_group",
    "settings_cloud_group",
    "settings_qa_group",
]

PRIVACY_FIRST = ("settings_capture_group", "places_title", "settings_data_privacy_group")
MUST_COME_LATER = (
    "settings_appearance_group",
    "settings_cloud_group",
    "settings_backup_group",
)

EXPECTED_CATEGORY_TAGS = [
    "settings_category_capture",
    "settings_category_privacy",
    "settings_category_journal",
    "settings_category_connected",
    "settings_category_developer",
]


def _group_order() -> list[str]:
    """Every SettingsGroup title in the order it appears in the composable."""
    source = SETTINGS_SCREEN.read_text(encoding="utf-8")
    order = []
    for line in source.splitlines():
        stripped = line.strip()
        if not stripped.startswith("SettingsGroup(title = stringResource(R.string."):
            continue
        name = stripped.split("R.string.", 1)[1].split(")", 1)[0]
        order.append(name)
    return order


def test_settings_screen_exists():
    assert SETTINGS_SCREEN.is_file(), f"{SETTINGS_SCREEN} is missing"


def test_settings_index_uses_focused_categories_in_privacy_first_order():
    source = SETTINGS_SCREEN.read_text(encoding="utf-8")
    menu = source.split("private fun SettingsCategoryMenu", 1)[1].split(
        "private fun SettingsCategoryButton", 1
    )[0]
    positions = [menu.index(f'\"{tag}\"') for tag in EXPECTED_CATEGORY_TAGS]
    assert positions == sorted(positions)


def test_settings_categories_use_progressive_disclosure_and_android_back():
    source = SETTINGS_SCREEN.read_text(encoding="utf-8")
    assert "BackHandler(enabled = activeSection != null)" in source
    assert "if (activeSection == null)" in source
    for section in (
        "CAPTURE_AND_PLACES",
        "PRIVACY_AND_DATA",
        "JOURNAL_AND_APPEARANCE",
        "BACKUP_AND_CLOUD",
    ):
        assert f"activeSection == SettingsSection.{section}" in source


def test_every_expected_group_is_still_present():
    order = _group_order()
    missing = [name for name in EXPECTED_ORDER if name not in order]
    assert not missing, (
        f"Settings groups disappeared from the screen: {missing}. "
        "If a group was intentionally removed, update EXPECTED_ORDER and say why in the commit."
    )


def test_no_unexpected_group_was_added_without_deciding_its_place():
    order = _group_order()
    unexpected = [name for name in order if name not in EXPECTED_ORDER]
    assert not unexpected, (
        f"New Settings group(s) {unexpected} were added without being placed in EXPECTED_ORDER. "
        "Decide deliberately whether they belong before or after the privacy groups."
    )


def test_groups_appear_in_the_order_the_brief_requires():
    assert _group_order() == EXPECTED_ORDER, (
        "Settings group order changed.\n"
        f"  expected: {EXPECTED_ORDER}\n"
        f"  actual:   {_group_order()}"
    )


def test_capture_and_named_places_precede_appearance_cloud_and_backup():
    """The brief's actual requirement, asserted independently of the exact full ordering.

    This is the test that must not be relaxed. `test_groups_appear_in_the_order_the_brief_requires`
    can legitimately be updated when a group is added or renamed; this one encodes the product
    constraint itself.
    """
    order = _group_order()
    positions = {name: order.index(name) for name in order}

    latest_privacy_group = max(positions[name] for name in PRIVACY_FIRST)
    for name in MUST_COME_LATER:
        assert positions[name] > latest_privacy_group, (
            f"'{name}' is shown before capture/privacy and named places. "
            "Named places is where private zones are declared, and a private zone is what keeps "
            "an address out of every cloud report and export. It cannot sit below provider setup."
        )
