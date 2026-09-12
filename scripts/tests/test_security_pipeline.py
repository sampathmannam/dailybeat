"""Policy tests for the layered open-source security gate."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
SECURITY_WORKFLOW = ROOT / ".github" / "workflows" / "security.yml"


def _workflow() -> str:
    return SECURITY_WORKFLOW.read_text(encoding="utf-8")


def test_oss_security_gate_keeps_all_scanner_layers():
    workflow = _workflow()

    for required in (
        "oss-security:",
        "gitleaks/gitleaks-action@",
        "trufflesecurity/trufflehog@",
        "semgrep==",
        "opengrep_manylinux_x86",
        "aquasecurity/trivy-action@",
        "--only-verified",
        "--error .",
        'exit-code: "1"',
    ):
        assert required in workflow, f"security workflow lost required control: {required}"


def test_third_party_actions_are_immutable_commit_pins():
    action_pattern = re.compile(r"uses:\s+[^\s@]+@([^\s#]+)")
    pins = action_pattern.findall(_workflow())

    assert pins
    assert all(re.fullmatch(r"[0-9a-f]{40}", pin) for pin in pins)


def test_opengrep_download_is_checksum_verified():
    workflow = _workflow()

    assert "OPENGREP_SHA256:" in workflow
    assert "sha256sum --check --strict" in workflow
