"""Guards the order of the groups on the Settings screen.

The product brief is explicit: capture/privacy and named places come before appearance, Cloud AI
and backup. The released v3.9 screen did the opposite — Appearance opened the screen and Named
places was dead last, below API-key configuration and a debug-only QA group.

That ordering is not cosmetic. Named places is where a private zone is declared, and a private
zone is the mechanism that keeps a home address out of every cloud report, geocoder call and
shared export. Burying it under a base-URL field buries the app's privacy contract.

Order is a layout property, so nothing in the JVM unit tests can see it and nothing short of a
device can assert it in Compose. This guard reads the source instead, so a future edit that drags
Appearance back to the top fails the local gate rather than shipping.
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
    "settings_appearance_group",
    "officer_name_label",
    "settings_backup_group",
    "settings_cloud_group",
    "settings_qa_group",
]

PRIVACY_FIRST = ("settings_capture_group", "places_title")
MUST_COME_LATER = (
    "settings_appearance_group",
    "settings_cloud_group",
    "settings_backup_group",
)


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
