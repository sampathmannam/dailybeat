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
        "gitleaks_8.30.1_linux_x64.tar.gz",
        "trufflehog_3.97.4_linux_amd64.tar.gz",
        "semgrep==",
        "opengrep_manylinux_x86",
        "trivy_0.74.0_Linux-64bit.tar.gz",
        "--only-verified",
        "--error .",
        "--exit-code 1",
    ):
        assert required in workflow, f"security workflow lost required control: {required}"


def test_third_party_actions_are_immutable_commit_pins():
    action_pattern = re.compile(r"uses:\s+[^\s@]+@([^\s#]+)")
    pins = action_pattern.findall(_workflow())

    assert pins
    assert all(re.fullmatch(r"[0-9a-f]{40}", pin) for pin in pins)


def test_scanner_downloads_are_checksum_verified():
    workflow = _workflow()

    assert "OPENGREP_SHA256:" in workflow
    assert "GITLEAKS_SHA256:" in workflow
    assert "TRUFFLEHOG_SHA256:" in workflow
    assert "TRIVY_SHA256:" in workflow
    assert workflow.count("sha256sum --check --strict") == 4
