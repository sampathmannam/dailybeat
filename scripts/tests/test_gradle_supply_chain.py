"""Guards for Gradle dependency verification.

The policy is deliberately CHECKSUM-ONLY. The Android dependency graph has no reliable common
signing-key chain, so a pinned SHA-256 per artifact is the portable baseline that actually holds in
CI. Signature verification looks stronger but fails closed on plugin-marker POMs whose keys cannot
be fetched from any key server, and the usual "fix" for that is to add a trusted-key or
trusted-artifact bypass — which is strictly worse than the checksums it replaces.

Regenerating with `--write-verification-metadata sha256,pgp` silently flips verify-signatures to
true. Always regenerate with `sha256` alone. These tests exist so that mistake fails the release
gate instead of CI a day later.
"""

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "android/gradle/verification-metadata.xml"
NS = {"v": "https://schema.gradle.org/dependency-verification"}


def _configuration() -> ET.Element:
    root = ET.parse(METADATA).getroot()
    configuration = root.find("v:configuration", NS)
    assert configuration is not None, "verification-metadata.xml has no <configuration> block"
    return configuration


def test_dependency_verification_metadata_is_present():
    assert METADATA.is_file(), (
        "android/gradle/verification-metadata.xml is missing. Without it every Gradle dependency "
        "resolves unpinned and a substituted artifact would be linked into a signed release."
    )


def test_verification_is_enabled_and_checksum_only():
    configuration = _configuration()

    verify_metadata = configuration.find("v:verify-metadata", NS)
    assert verify_metadata is not None and verify_metadata.text.strip() == "true"

    verify_signatures = configuration.find("v:verify-signatures", NS)
    assert verify_signatures is not None, "<verify-signatures> must be stated explicitly"
    assert verify_signatures.text.strip() == "false", (
        "verify-signatures is true. This usually means the metadata was regenerated with "
        "`--write-verification-metadata sha256,pgp`. Regenerate with `sha256` only."
    )


def test_no_verification_bypasses_are_configured():
    configuration = _configuration()

    for bypass in ("trusted-keys", "ignored-keys", "trusted-artifacts"):
        element = configuration.find(f"v:{bypass}", NS)
        assert element is None or len(list(element)) == 0, (
            f"<{bypass}> is populated. Every entry there exempts an artifact from the checksum "
            "policy, so the pinning it replaces is no longer enforced."
        )


def test_every_component_pins_a_sha256():
    root = ET.parse(METADATA).getroot()
    components = root.find("v:components", NS)
    assert components is not None and len(list(components)) > 0, "no components are pinned"

    unpinned = []
    for component in components.findall("v:component", NS):
        for artifact in component.findall("v:artifact", NS):
            if artifact.find("v:sha256", NS) is None:
                unpinned.append(
                    f"{component.get('group')}:{component.get('name')}:"
                    f"{component.get('version')}/{artifact.get('name')}"
                )

    assert not unpinned, "artifacts without a sha256 pin: " + ", ".join(sorted(unpinned)[:10])


def test_the_linux_aapt2_variant_is_pinned_for_ci():
    # aapt2 is the only platform-classified artifact in the graph. Regenerating on macOS records
    # only the osx variant, while every CI job runs on ubuntu-latest — so the linux jar has to be
    # present or the build fails closed on the runner and never locally.
    text = METADATA.read_text(encoding="utf-8")
    assert "aapt2" in text, "aapt2 is not pinned at all"
    assert "linux" in text, (
        "no linux aapt2 variant is pinned. CI runs on ubuntu-latest and will fail dependency "
        "verification even though a macOS build passes."
    )
