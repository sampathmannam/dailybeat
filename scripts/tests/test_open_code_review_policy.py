import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / ".opencodereview" / "rule.json"


def test_open_code_review_policy_covers_dailybeat_trust_boundaries():
    policy = json.loads(POLICY.read_text(encoding="utf-8"))
    rules = {entry["path"]: entry for entry in policy["rules"]}

    required_patterns = {
        "android/app/src/main/java/com/dailybeat/app/capture/**/*.kt",
        "android/app/src/{gms,foss}/java/com/dailybeat/app/capture/**/*.kt",
        "android/app/src/main/java/com/dailybeat/app/backup/**/*.kt",
        "android/app/src/main/java/com/dailybeat/app/data/**/*.kt",
        "android/app/src/main/java/com/dailybeat/app/{cloud,geo,export}/**/*.kt",
        "android/app/src/main/java/com/dailybeat/app/ui/**/*.kt",
        "android/app/src/main/AndroidManifest.xml",
        "supabase/{migrations,tests}/**/*.sql",
        ".github/workflows/**/*.yml",
        ".github/scripts/**/*.sh",
        "scripts/**/*.{py,sh}",
    }
    assert required_patterns == rules.keys()
    assert all(rule.get("merge_system_rule") is True for rule in rules.values())

    combined = " ".join(rule["rule"].lower() for rule in rules.values())
    for required_concern in (
        "battery",
        "foreground-service",
        "permission",
        "data loss",
        "authenticated encryption",
        "migration",
        "rls",
        "accessibility",
        "immutable",
        ".qa.e2eloop",
    ):
        assert required_concern in combined


def test_open_code_review_policy_includes_tests_and_excludes_generated_artifacts():
    policy = json.loads(POLICY.read_text(encoding="utf-8"))

    assert "android/app/src/test/**/*.kt" in policy["include"]
    assert "android/app/src/androidTest/**/*.kt" in policy["include"]
    assert "scripts/tests/**/*.py" in policy["include"]
    assert "android/**/build/**" in policy["exclude"]
    assert "docs/dependency-license-inventory.json" in policy["exclude"]
    assert "outputs/**" in policy["exclude"]
    assert "**/*.keystore" in policy["exclude"]
