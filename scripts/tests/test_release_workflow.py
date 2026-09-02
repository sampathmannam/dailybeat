from pathlib import Path
import re
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
POLICY = WORKFLOW.split("  policy-tests:", 1)[1].split("\n  build-candidate:", 1)[0]
BUILD = WORKFLOW.split("  build-candidate:", 1)[1].split("\n  publish-draft:", 1)[0]
PUBLISH = WORKFLOW.split("\n  publish-draft:", 1)[1]


def test_release_has_separate_policy_unsigned_build_and_ciphertext_publish_jobs():
    assert "  policy-tests:" in WORKFLOW
    assert "  build-candidate:" in WORKFLOW
    assert "  publish-draft:" in WORKFLOW
    assert "sign-and-package" not in WORKFLOW
    assert "Restore permanent signing key" not in WORKFLOW
    assert "Sign APK" not in WORKFLOW
    assert "release.keystore" not in WORKFLOW
    assert "--ks " not in WORKFLOW
    assert "app-release-unsigned.apk" in BUILD
    assert "candidate is unexpectedly signed" in BUILD


def test_cloud_never_receives_permanent_or_symmetric_signing_secrets():
    forbidden = (
        "PATROLGRID_RELEASE_KEYSTORE_BASE64",
        "PATROLGRID_RELEASE_STORE_PASSWORD",
        "PATROLGRID_RELEASE_KEY_PASSWORD",
        "PATROLGRID_ARTIFACT_TRANSFER_KEY",
        "DAILYBEAT_KEYSTORE_BASE64",
        "DAILYBEAT_STORE_PASSWORD",
        "DAILYBEAT_KEY_PASSWORD",
        "--symmetric",
        "--passphrase-fd",
        "AES256",
    )
    for value in forbidden:
        assert value not in WORKFLOW


def test_candidate_is_public_key_encrypted_to_exact_committed_subkey():
    assert "release/patrolgrid-release-public-key.asc" in BUILD
    assert "AA2B9126F5750A6690CEA90410B087D428F60413" in BUILD
    assert "84AD08D70EC95222457C16CEFEFD926C2C74FB9E" in BUILD
    assert "84AD08D70EC95222457C16CEFEFD926C2C74FB9E!" in BUILD
    assert "--recipient \"$ENCRYPTION_RECIPIENT\" --encrypt" in BUILD
    assert "--no-auto-key-retrieve" in BUILD
    assert "verify_patrolgrid_openpgp_packets.py" in BUILD
    assert "FEFD926C2C74FB9E" in BUILD
    assert "--decrypt" not in WORKFLOW


def test_candidate_filename_and_archive_bind_full_commit_and_all_inputs():
    assert "PatrolGrid-%s-%s-unsigned-candidate.tar.gpg" in BUILD
    assert "PatrolGrid-$VERSION_NAME-$GITHUB_SHA-unsigned.apk" in BUILD
    assert "PatrolGrid-$VERSION_NAME-$GITHUB_SHA-mapping.txt" in BUILD
    assert "PatrolGrid-$VERSION_NAME-$GITHUB_SHA.spdx.json" in BUILD
    assert "PatrolGrid-$VERSION_NAME-$GITHUB_SHA-candidate.json" in BUILD
    assert "--format=ustar" in BUILD
    assert "--mtime='UTC 1970-01-01'" in BUILD
    assert "schemaVersion:1" in BUILD
    for field in (
        "supabaseAnonKeySha256",
        "supabaseUrlSha256",
        "backendIdentity",
        "privacyPolicyStatus",
        "privacyNoticeVersion",
        "privacyPolicySha256",
        "unsignedApk:{name:",
        "mapping:{name:",
        "sbom:{name:",
        "runAttempt",
        "runId",
    ):
        assert field in BUILD
    assert "SUPABASE_ANON_KEY: ${{ secrets.PATROLGRID_PRODUCTION_SUPABASE_ANON_KEY }}" in BUILD
    assert "PATROLGRID_RELEASE_COMMIT: ${{ github.sha }}" in BUILD
    assert "PATROLGRID_BACKEND_IDENTITY:" not in BUILD
    assert "PATROLGRID_PRIVACY_POLICY_STATUS:" not in BUILD
    assert "PATROLGRID_PRIVACY_NOTICE_VERSION:" not in BUILD
    assert "com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS" in BUILD
    assert "com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION" in BUILD
    assert '.privacyPolicy.status == "APPROVED"' in BUILD
    assert ".privacyPolicy.noticeVersion == 3" in BUILD


def test_only_one_ciphertext_crosses_jobs_and_upload_outputs_are_bound():
    assert WORKFLOW.count("uses: actions/upload-artifact@") == 1
    assert "path: sealed/${{ steps.identity.outputs.candidate_name }}" in BUILD
    assert "retention-days: 1" in BUILD
    assert "compression-level: 0" in BUILD
    assert "artifact_id: ${{ steps.upload.outputs.artifact-id }}" in BUILD
    assert "artifact_digest: ${{ steps.upload.outputs.artifact-digest }}" in BUILD
    assert "ciphertext_digest: ${{ steps.seal.outputs.ciphertext_digest }}" in BUILD
    assert "Remove every plaintext candidate before upload" in BUILD
    assert BUILD.index("Remove every plaintext candidate before upload") < BUILD.index(
        "uses: actions/upload-artifact@"
    )
    assert "*.apk" not in BUILD.split("name: Upload one ciphertext candidate", 1)[1]


def test_publish_uses_only_github_rest_and_no_checkout_or_download_release_action():
    assert "uses:" not in PUBLISH
    assert "actions/checkout@" not in PUBLISH
    assert "actions/download-artifact@" not in PUBLISH
    assert "softprops/action-gh-release" not in PUBLISH
    assert "gh release" not in PUBLISH
    assert "actions/artifacts/$ARTIFACT_ID" in PUBLISH
    assert "actions/artifacts/$ARTIFACT_ID/zip" in PUBLISH
    assert ".workflow_run.id == $run" in PUBLISH
    assert ".workflow_run.head_sha == $sha" in PUBLISH
    assert ".digest == $digest" in PUBLISH
    assert "Downloaded artifact digest does not match" in PUBLISH
    assert "Extracted ciphertext digest does not match" in PUBLISH
    assert "ARTIFACT_DIGEST" in PUBLISH
    assert "CIPHERTEXT_DIGEST" in PUBLISH


def _run_digest_normalizer(upload_digest: str, api_digest: str = "") -> subprocess.CompletedProcess[str]:
    start = PUBLISH.index("          normalize_upload_artifact_digest() {")
    end = PUBLISH.index("\n          [[ \"$CANDIDATE_NAME\"", start)
    function = PUBLISH[start:end]
    command = function + "\nnormalize_upload_artifact_digest \"$1\" \"$2\"\n"
    return subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", "-c", command, "digest-test", upload_digest,
         api_digest],
        text=True,
        capture_output=True,
        check=False,
    )


def test_upload_output_plain_hex_normalizes_to_exact_rest_digest():
    digest = "a" * 64
    result = _run_digest_normalizer(digest, "sha256:" + digest)
    assert result.returncode == 0, result.stderr
    assert result.stdout == "sha256:" + digest + "\n"


@pytest.mark.parametrize(
    ("upload_digest", "api_digest"),
    [
        ("sha256:" + "a" * 64, "sha256:" + "a" * 64),
        ("A" * 64, "sha256:" + "A" * 64),
        ("a" * 63, "sha256:" + "a" * 63),
        ("a" * 64, "sha512:" + "a" * 64),
        ("a" * 64, "a" * 64),
        ("a" * 64, "sha256:" + "b" * 64),
    ],
)
def test_upload_artifact_digest_normalizer_rejects_format_or_algorithm_confusion(
    upload_digest: str, api_digest: str
):
    assert _run_digest_normalizer(upload_digest, api_digest).returncode != 0


def test_publish_safely_extracts_exact_one_entry_zip():
    for invariant in (
        "len(members) != 1",
        "members[0].filename != expected",
        "path.is_absolute()",
        '".." in path.parts',
        "member.is_dir()",
        "not stat.S_ISREG(mode)",
        "os.O_EXCL",
        'getattr(os, "O_NOFOLLOW", 0)',
    ):
        assert invariant in PUBLISH
    assert "extractall" not in PUBLISH
    assert "tar -x" not in PUBLISH


def test_publish_creates_or_updates_exact_draft_and_verifies_server_asset():
    assert "draft:true" in PUBLISH
    assert "prerelease:false" in PUBLISH
    assert "make_latest:\"false\"" in PUBLISH
    assert ".published_at == null" in PUBLISH
    assert "(.assets|length) == 1" in PUBLISH
    assert '.uploader.login == "github-actions[bot]"' in PUBLISH
    assert '.target_commitish == $commit' not in PUBLISH
    assert "target_commitish is not authoritative" in PUBLISH
    assert "uploads.github.com/repos/$GITHUB_REPOSITORY/releases/$release_id/assets" in PUBLISH
    assert "--data-binary" in PUBLISH
    assert "draft:false" not in WORKFLOW.replace("draft == false", "")
    assert "app-release.apk" not in PUBLISH
    assert "mapping.txt" not in PUBLISH


def test_tag_main_and_live_ruleset_governance_are_exactly_rechecked():
    for job in (BUILD, PUBLISH):
        assert "22066728" in job
        assert "22066729" in job
        assert "22066730" in job
        assert ".verification.verified == true" in job
        assert '.verification.reason == "valid"' in job
        assert ".merge_base_commit.sha == $commit" in job
        assert '.status == "identical"' in job
    assert "PatrolGrid protected main" in BUILD
    assert '[[ "$main_sha" == "$GITHUB_SHA" ]]' in BUILD
    assert '[[ "$main_sha" == "$GITHUB_SHA" ]]' in PUBLISH
    assert "git merge-base --is-ancestor" in BUILD
    assert "actions/checkout@" not in PUBLISH


def test_release_preflight_includes_supply_chain_and_all_policy_tests():
    for test_name in (
        "test_android_release_inputs.py",
        "test_gradle_supply_chain.py",
        "test_mac_patrolgrid_helpers.py",
        "test_mac_release_decrypt.py",
        "test_patrolgrid_publish_release.py",
        "test_release_pipeline.py",
        "test_release_workflow.py",
    ):
        assert test_name in POLICY
    assert "pip install" not in BUILD
    assert "--require-hashes" in POLICY
    assert "needs: policy-tests" in BUILD
    assert "--dependency-verification strict" in BUILD
    assert "--no-build-cache" in BUILD
    assert "lintRelease" in BUILD
    assert "testReleaseUnitTest" in BUILD


def test_all_external_actions_are_full_sha_pinned():
    action_refs = re.findall(r"^\s*- uses: ([^#\s]+)", WORKFLOW, flags=re.MULTILINE)
    assert action_refs
    assert all(re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref.split(" #", 1)[0]) for ref in action_refs)


def test_job_permissions_are_separate_and_minimal():
    assert "permissions:\n      contents: read" in POLICY
    assert "permissions:\n      contents: read" in BUILD
    assert "contents: write" not in BUILD
    assert "actions: read" in PUBLISH
    assert "contents: write" in PUBLISH
    assert "environment: patrolgrid-production" in BUILD
    assert "environment: patrolgrid-production" in PUBLISH


def test_admin_only_governance_is_not_requested_with_github_token():
    assert "/actions/permissions" not in WORKFLOW
    assert "/environments/patrolgrid-production" not in WORKFLOW
    assert ".bypass_actors" not in WORKFLOW


def test_live_signed_squash_governance_and_security_checks_are_bound():
    for job in (BUILD, PUBLISH):
        assert '"required_signatures"' in job
        for context in ("build", "patrolgrid-backend", "dependency-review", "codeql"):
            assert f'context:"{context}",integration_id:15368' in job
