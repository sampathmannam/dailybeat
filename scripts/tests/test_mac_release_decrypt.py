from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
import importlib.util
import re
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "scripts/mac_decrypt_patrolgrid_release.sh"
SOURCE = HELPER.read_text(encoding="utf-8")
PUBLIC_KEY = ROOT / "release/patrolgrid-release-public-key.asc"
CERTIFICATE = ROOT / "release/patrolgrid-release-cert.pem"
PRIMARY = "AA2B9126F5750A6690CEA90410B087D428F60413"
SUBKEY = "84AD08D70EC95222457C16CEFEFD926C2C74FB9E"  # gitleaks:allow -- public subkey fingerprint
CERT = "1b1351160170796ec9818047e790a5474c8544ad867f62736e8d93fe2a8c025b"
MANIFEST_VERIFIER = ROOT / "scripts/verify_patrolgrid_release_manifest.py"


def test_helper_has_only_check_and_authenticated_ceremony_interfaces():
    assert "mac_decrypt_patrolgrid_release.sh check" in SOURCE
    assert "mac_decrypt_patrolgrid_release.sh ceremony" in SOURCE
    assert "decrypt-package" not in SOURCE
    assert "decrypt-mapping" not in SOURCE
    assert "setup-key" not in SOURCE
    assert "local ciphertext" in SOURCE
    assert "repository override" in SOURCE
    assert "key path" in SOURCE


def test_fixed_offline_key_paths_and_keychain_items_are_not_overridable():
    assert (
        "RELEASE_GNUPGHOME='/Users/sujithsampath/Library/Application Support/"
        "PatrolGrid/gnupg'"
    ) in SOURCE
    assert (
        "RELEASE_KEYSTORE='/Users/sujithsampath/Library/Application Support/"
        "PatrolGrid/signing/patrolgrid-release.p12'"
    ) in SOURCE
    assert "DECRYPTION_SERVICE='PatrolGrid release decryption'" in SOURCE
    assert "APK_SIGNING_SERVICE='PatrolGrid APK signing'" in SOURCE
    assert "KEYCHAIN_ACCOUNT='sampathmannam/dailybeat'" in SOURCE
    assert "RELEASE_KEY_ALIAS='patrolgrid'" in SOURCE
    assert "PATROLGRID_ARTIFACT_TRANSFER_KEY" not in SOURCE
    assert "PATROLGRID_RELEASE_KEYSTORE" not in SOURCE
    assert "--passphrase " not in SOURCE
    assert "--ks-pass stdin" in SOURCE
    assert "--key-pass stdin" in SOURCE
    assert "env:" not in SOURCE
    assert "pass:" not in SOURCE


def test_exact_openpgp_primary_and_encryption_subkey_are_pinned():
    assert PRIMARY in SOURCE
    assert SUBKEY in SOURCE
    assert '"$ENCRYPTION_FINGERPRINT!"' in SOURCE
    assert "--try-secret-key \"$ENCRYPTION_FINGERPRINT!\"" in SOURCE
    assert "DECRYPTION_KEY $ENCRYPTION_FINGERPRINT $PRIMARY_FINGERPRINT" in SOURCE
    assert "ENC_TO $ENCRYPTION_KEY_ID" in SOURCE
    assert "DECRYPTION_OKAY" in SOURCE
    assert "--no-auto-key-retrieve" in SOURCE
    listing = subprocess.run(
        ["gpg", "--show-keys", "--with-colons", str(PUBLIC_KEY)],
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    fingerprints = [line.split(":")[9] for line in listing.splitlines() if line.startswith("fpr:")]
    assert fingerprints == [PRIMARY, SUBKEY]


def test_openpgp_packet_policy_rejects_a_real_dual_public_and_symmetric_recipient(
    tmp_path: Path,
):
    gpg = _tool("gpg", "/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpg")
    if not gpg:
        pytest.skip("GnuPG is unavailable")
    home = Path(tempfile.mkdtemp(prefix=".patrolgrid-gpg-test.", dir="/tmp"))
    home.chmod(0o700)
    imported = subprocess.run(
        [gpg, "--homedir", home, "--no-options", "--batch", "--import", PUBLIC_KEY],
        text=True,
        capture_output=True,
        check=False,
    )
    assert imported.returncode == 0, imported.stderr
    plaintext = tmp_path / "candidate.tar"
    plaintext.write_bytes(b"candidate fixture")
    public_only = tmp_path / "public-only.gpg"
    common = [
        gpg, "--homedir", home, "--no-options", "--batch", "--yes",
        "--trust-model", "always", "--recipient", SUBKEY + "!",
    ]
    encrypted = subprocess.run(
        [*common, "--encrypt", "--output", public_only, plaintext],
        text=True,
        capture_output=True,
        check=False,
    )
    assert encrypted.returncode == 0, encrypted.stderr
    verifier = ROOT / "scripts/verify_patrolgrid_openpgp_packets.py"
    accepted = subprocess.run(
        [sys.executable, "-I", verifier, public_only, SUBKEY[-16:]],
        text=True,
        capture_output=True,
        check=False,
    )
    assert accepted.returncode == 0, accepted.stderr
    assert "OPENPGP_RECIPIENT_OK" in accepted.stdout

    dual = tmp_path / "dual-recipient.gpg"
    dual_result = subprocess.run(
        [*common, "--pinentry-mode", "loopback", "--passphrase-fd", "0",
         "--symmetric", "--encrypt", "--output", dual, plaintext],
        input="fixture-only-passphrase\n",
        text=True,
        capture_output=True,
        check=False,
    )
    assert dual_result.returncode == 0, dual_result.stderr
    rejected = subprocess.run(
        [sys.executable, "-I", verifier, dual, SUBKEY[-16:]],
        text=True,
        capture_output=True,
        check=False,
    )
    assert rejected.returncode != 0
    assert "additional recipient" in rejected.stderr
    gpgconf = _tool("gpgconf", "/opt/homebrew/Cellar/gnupg/2.5.20/bin/gpgconf")
    if gpgconf:
        subprocess.run([gpgconf, "--homedir", home, "--kill", "gpg-agent"], check=False)
    shutil.rmtree(home, ignore_errors=True)


def test_openpgp_packet_policy_streams_a_declared_gigabyte_packet(tmp_path: Path):
    """A hostile length must fail as truncation without a length-sized allocation."""
    ciphertext = tmp_path / "declared-gigabyte.gpg"
    recipient = bytes.fromhex(SUBKEY[-16:])
    ciphertext.write_bytes(
        bytes([0x84, 10, 3]) + recipient + bytes([18, 0xD4, 0xFF]) +
        (1024 * 1024 * 1024).to_bytes(4, "big")
    )
    verifier = ROOT / "scripts/verify_patrolgrid_openpgp_packets.py"

    result = subprocess.run(
        [sys.executable, "-I", verifier, ciphertext, SUBKEY[-16:]],
        text=True,
        capture_output=True,
        check=False,
        timeout=10,
    )
    assert result.returncode != 0
    assert "ended inside an OpenPGP packet" in result.stderr
    assert "MemoryError" not in result.stderr

    specification = importlib.util.spec_from_file_location(
        "openpgp_packet_policy_fixture", verifier
    )
    assert specification and specification.loader
    policy = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(policy)

    class TruncatedSource:
        requests: list[int] = []

        def read(self, length: int) -> bytes:
            self.requests.append(length)
            return b""

    guarded = TruncatedSource()
    with pytest.raises(SystemExit, match="ended inside"):
        policy.discard_exact(guarded, 1024 * 1024 * 1024)
    assert guarded.requests == [policy.DISCARD_CHUNK_SIZE]


def test_committed_apk_certificate_matches_new_pinned_digest():
    result = subprocess.run(
        ["openssl", "x509", "-in", str(CERTIFICATE), "-outform", "DER"],
        capture_output=True,
        check=True,
    )
    import hashlib

    assert hashlib.sha256(result.stdout).hexdigest() == CERT
    assert CERT in SOURCE
    assert "44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f" not in SOURCE


def test_ceremony_verifies_live_github_provenance_and_exact_main_head():
    for value in (
        "22066728",
        "22066729",
        "22066730",
        "PatrolGrid protected main",
        "PatrolGrid release tag creator",
        "PatrolGrid immutable release tags",
        ".verification.verified == true",
        '.verification.reason == "valid"',
        '[[ "$main_sha" == "$RESOLVED_COMMIT" ]]',
        '.status == "identical"',
        '.status == "completed"',
        '.conclusion == "success"',
        '.path == ".github/workflows/release.yml"',
        ".workflow_run.head_sha == $sha",
        "uploader.login",
    ):
        assert value in SOURCE
    assert "ahead\" or" not in SOURCE


def test_owner_mac_binds_repository_security_controls_not_cloud_token():
    for value in (
        'github_get "/user" "$TEMP_ROOT/authenticated-owner.json"',
        '"GitHub CLI is not authenticated as the PatrolGrid repository owner"',
        '"authenticated PatrolGrid owner does not retain repository admin authority"',
        "/collaborators/sampathmannam/permission",
        "vulnerability-alerts",
        "automated-security-fixes",
        "private-vulnerability-reporting",
        ".security_and_analysis.secret_scanning.status",
        ".security_and_analysis.secret_scanning_push_protection.status",
        ".security_and_analysis.dependabot_security_updates.status",
    ):
        assert value in SOURCE
    workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    assert "/vulnerability-alerts" not in workflow
    assert "/automated-security-fixes" not in workflow


def test_archive_and_outputs_are_strictly_allowlisted_and_race_resistant():
    publisher = (ROOT / "scripts/patrolgrid_publish_release.py").read_text(encoding="utf-8")
    assert "candidate archive allowlist/order is invalid" in SOURCE
    assert "candidate archive member type, metadata, or size is unsafe" in SOURCE
    assert "os.O_EXCL" in SOURCE
    assert 'getattr(os, "O_NOFOLLOW", 0)' in SOURCE
    assert "MAX_DECRYPTED_ARCHIVE_BYTES" in SOURCE
    assert "MAX_UNSIGNED_APK_BYTES" in SOURCE
    assert 'tarfile.open(archive_name, mode="r|")' in SOURCE
    assert "getmembers()" not in SOURCE
    assert "tar -xf" not in SOURCE
    assert "release directory already exists" in SOURCE or "$kind already exists" in SOURCE
    assert "renamex_np" in publisher
    assert "RENAME_EXCL" in publisher
    assert "complete atomic tree" in publisher
    assert "bundle-parent-after-rename" in publisher
    assert ".patrolgrid-bundle.XXXXXX" in SOURCE
    assert '"$STAGED_BUNDLE_DIR/staff"' in SOURCE
    assert '"$STAGED_BUNDLE_DIR/owner"' in SOURCE


@pytest.mark.skipif(sys.platform != "darwin", reason="Mac private-copy boundary")
def test_verified_private_copy_rejects_executable_copy_corruption(tmp_path: Path):
    fake_install = tmp_path / "corrupting-install"
    fake_install.write_text(
        "#!/bin/bash\n/usr/bin/install \"$@\"\nprintf 'corruption' >> \"$4\"\n",
        encoding="utf-8",
    )
    fake_install.chmod(0o700)
    source_file = tmp_path / "verified"
    destination = tmp_path / "copied"
    source_file.write_bytes(b"verified release bytes")
    function_body = SOURCE.split("verified_private_copy() {", 1)[1].split("\n}\n", 1)[0]
    program = (
        "fail() { echo \"$*\" >&2; exit 91; }\n"
        "require_designated_private_file() { :; }\n"
        "verified_private_copy() {" + function_body + "\n}\n"
        "verified_private_copy \"$1\" \"$2\" fixture-copy\n"
    )
    result = subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", "-c", program, "copy-test",
         source_file, destination],
        env={"INSTALL": str(fake_install), "PATH": "/usr/bin:/bin"},
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode != 0
    assert "differs from its verified source" in result.stderr


def test_verified_apk_and_sbom_bytes_are_bound_through_publication():
    for exact_guard in (
        'verified_private_copy "$CANDIDATE_SBOM" "$final_dir/$sbom_name"',
        'verified_private_copy "$signed_apk" "$final_dir/$apk_name"',
        '/usr/bin/cmp -s "$PREPARED_RELEASE_DIR/$apk_name" "$signed_apk"',
        '/usr/bin/cmp -s "$PREPARED_RELEASE_DIR/$sbom_name" "$CANDIDATE_SBOM"',
        'verify_apk_identity "$PREPARED_RELEASE_DIR/$apk_name"',
        '"$SHASUM" -a 256 -c "$checksum_name"',
    ):
        assert exact_guard in SOURCE


def test_manifest_binds_backend_commit_workflow_and_all_hashes():
    for value in (
        "supabaseAnonKeySha256",
        "supabaseUrlSha256",
        "privacyPolicy.sha256",
        "candidate manifest schema/identity is invalid",
        "patrolgrid-production.properties?ref=$commit",
        "PATROLGRID_PRIVACY_POLICY.md?ref=$commit",
        "candidate artifact hash, size, or name",
        "ciphertextSha256",
        "manifestSha256",
        "unsignedApkSha256",
        "androidManifestSha256",
        "actionsArtifactArchiveSha256",
        "actionsArtifactId",
        "draftAssetId",
        "draftReleaseId",
        "mappingSha256",
        "sbomSha256",
    ):
        assert value in SOURCE
    assert "UNCONFIGURED" in SOURCE
    assert "RELEASE_COMMIT" in SOURCE
    assert "BACKEND_IDENTITY" in SOURCE


def test_apk_and_release_metadata_are_signed_offline_with_same_cert():
    assert SOURCE.count("sign_with_apk_key") >= 2
    assert "zipalign" in SOURCE.lower()
    assert SOURCE.index('"$ZIPALIGN" -P 16 -f 4 "$with_metadata_apk"') < SOURCE.index(
        'sign_with_apk_key "$aligned_apk"'
    )
    assert "assets/patrolgrid-release.json" in SOURCE
    assert "release metadata was not embedded exactly once" in SOURCE
    assert "APK-signed release metadata content changed" in SOURCE
    assert "Owner-only R8 mapping (never send to staff)" in SOURCE
    assert "find \"$final_dir\"" in SOURCE
    assert "== '3'" in SOURCE


def _load_manifest_policy():
    specification = importlib.util.spec_from_file_location("manifest_policy_fixture", MANIFEST_VERIFIER)
    assert specification and specification.loader
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def _manifest_policy_fixture(tmp_path: Path, mutation: str = "") -> tuple[list[str], Path]:
    policy = _load_manifest_policy()
    android = policy.ANDROID
    root = ET.Element(
        "manifest",
        {
            "package": policy.PACKAGE,
            android + "versionCode": "10000",
            android + "versionName": "1.0.0",
            android + "compileSdkVersion": "36",
            android + "compileSdkVersionCodename": "16",
            "platformBuildVersionCode": "36",
            "platformBuildVersionName": "16",
        },
    )
    sdk = ET.SubElement(root, "uses-sdk")
    sdk.set(android + "minSdkVersion", "26")
    sdk.set(android + "targetSdkVersion", "35" if mutation == "sdk" else "36")
    for permission in sorted(policy.PERMISSIONS):
        item = ET.SubElement(root, "uses-permission")
        item.set(android + "name", permission)
    if mutation == "permission":
        item = ET.SubElement(root, "uses-permission")
        item.set(android + "name", "android.permission.CAMERA")
    declaration = ET.SubElement(root, "permission")
    declaration.set(android + "name", policy.PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    declaration.set(android + "protectionLevel", "0x2")
    feature = ET.SubElement(root, "uses-feature")
    feature.set(android + "glEsVersion", "0x30000")
    feature.set(android + "required", "true")
    feature = ET.SubElement(root, "uses-feature")
    feature.set(android + "name", "android.hardware.wifi")
    feature.set(android + "required", "false")
    application = ET.SubElement(root, "application")
    application_values = {
        name: "@ref/0x7f0e0008" if value == policy.RESOURCE_REFERENCE else value
        for name, value in policy.APPLICATION_ATTRIBUTES.items()
    }
    application_values["networkSecurityConfig"] = "@ref/0x7f100002"
    application_values["dataExtractionRules"] = "@ref/0x7f100000"
    application_values["allowBackup"] = "true" if mutation == "backup" else "false"
    application_values["usesCleartextTraffic"] = "true" if mutation == "cleartext" else "false"
    for name, value in application_values.items():
        application.set(android + name, value)
    commit = "a" * 40
    backend = "https://example.supabase.co"
    for name, value in {
        "com.dailybeat.app.patrolgrid.RELEASE_COMMIT": commit,
        "com.dailybeat.app.patrolgrid.BACKEND_IDENTITY": backend,
        "com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS": "APPROVED",
        "com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION": "3",
    }.items():
        item = ET.SubElement(application, "meta-data")
        item.set(android + "name", name)
        item.set(android + "value", value)
    gms = ET.SubElement(application, "meta-data")
    gms.set(android + "name", "com.google.android.gms.version")
    gms.set(android + "value", "@ref/0x7f090000")
    if mutation == "duplicate-metadata":
        duplicate = ET.SubElement(application, "meta-data")
        duplicate.set(android + "name", "com.google.android.gms.version")
        duplicate.set(android + "value", "@ref/0x7f090000")
    component_objects = {}
    for component_type, names in policy.COMPONENTS.items():
        for name in sorted(names):
            component = ET.SubElement(application, component_type)
            component_objects[name] = component
            for attribute_name, value in policy.COMPONENT_ATTRIBUTES[name].items():
                component.set(
                    android + attribute_name,
                    "@ref/0x7f040000" if value == policy.RESOURCE_REFERENCE else value,
                )
            for actions, categories in policy.INTENT_FILTERS.get(name, []):
                intent_filter = ET.SubElement(component, "intent-filter")
                for action_name in actions:
                    action = ET.SubElement(intent_filter, "action")
                    action.set(android + "name", action_name)
                for category_name in categories:
                    category = ET.SubElement(intent_filter, "category")
                    category.set(android + "name", category_name)
    file_provider = component_objects["androidx.core.content.FileProvider"]
    file_provider.set(android + "exported", "true" if mutation == "exported" else "false")
    if mutation == "provider-authority":
        file_provider.set(android + "authorities", policy.PACKAGE + ".attacker")
    if mutation == "grant-uri":
        file_provider.set(android + "grantUriPermissions", "false")
    file_paths = ET.SubElement(file_provider, "meta-data")
    file_paths.set(android + "name", "android.support.FILE_PROVIDER_PATHS")
    file_paths.set(android + "resource", "@ref/0x7f100001")
    if mutation == "foreground-service":
        component_objects["com.dailybeat.app.capture.LocationService"].set(
            android + "foregroundServiceType", "0x1"
        )
    startup = component_objects["androidx.startup.InitializationProvider"]
    for name in (
        "androidx.work.WorkManagerInitializer",
        "androidx.emoji2.text.EmojiCompatInitializer",
        "androidx.lifecycle.ProcessLifecycleInitializer",
        "androidx.profileinstaller.ProfileInstallerInitializer",
    ):
        item = ET.SubElement(startup, "meta-data")
        item.set(android + "name", name)
        item.set(android + "value", "androidx.startup")
    if mutation == "deeplink":
        intent_filter = component_objects["com.dailybeat.app.MainActivity"].find("intent-filter")
        assert intent_filter is not None
        data = ET.SubElement(intent_filter, "data")
        data.set(android + "scheme", "https")
    if mutation == "intent-attribute":
        intent_filter = component_objects["com.dailybeat.app.MainActivity"].find("intent-filter")
        assert intent_filter is not None
        intent_filter.set(android + "autoVerify", "true")
    if mutation == "component-metadata":
        extra = ET.SubElement(component_objects["com.dailybeat.app.MainActivity"], "meta-data")
        extra.set(android + "name", "attacker")
        extra.set(android + "value", "enabled")
    manifest = tmp_path / "AndroidManifest.xml"
    ET.ElementTree(root).write(manifest, encoding="utf-8", xml_declaration=True)
    network = tmp_path / "network.txt"
    network.write_text(
        policy.NETWORK_POLICY.replace('src="system"', 'src="user"')
        if mutation == "network" else policy.NETWORK_POLICY,
        encoding="utf-8",
    )
    extraction = tmp_path / "extraction.txt"
    extraction.write_text(policy.EXTRACTION_POLICY, encoding="utf-8")
    file_paths_policy = tmp_path / "file-paths.txt"
    file_paths_policy.write_text(
        "E: paths\n  E: root-path\n    A: name=\"all\" (Raw: \"all\")\n"
        "    A: path=\".\" (Raw: \".\")\n"
        if mutation == "root-path" else policy.FILE_PATHS_POLICY,
        encoding="utf-8",
    )
    resources = tmp_path / "resources.txt"
    network_name = (
        "permissive_network_config" if mutation == "resource-confusion" else "network_security_config"
    )
    network_value_name = "permissive_network_config" if mutation == "network-concrete-path" else network_name
    file_paths_name = "root_paths" if mutation == "file-resource-confusion" else "file_paths"
    resource_lines = [
        "      spec resource 0x7f100000 "
        f"{policy.PACKAGE}:xml/data_extraction_rules: flags=0x00000000",
        f"      spec resource 0x7f100001 {policy.PACKAGE}:xml/{file_paths_name}: flags=0x00000000",
        f"      spec resource 0x7f100002 {policy.PACKAGE}:xml/{network_name}: flags=0x00000000",
    ]
    if mutation == "resource-confusion":
        resource_lines.append(
            f"      spec resource 0x7f100003 {policy.PACKAGE}:xml/network_security_config: "
            "flags=0x00000000"
        )
    if mutation == "file-resource-confusion":
        resource_lines.append(
            f"      spec resource 0x7f100004 {policy.PACKAGE}:xml/file_paths: flags=0x00000000"
        )
    resource_lines.extend([
        "    config (default):",
        f"      resource 0x7f100000 {policy.PACKAGE}:xml/data_extraction_rules: t=0x03 d=0x00000000",
        '        (string8) "res/xml/data_extraction_rules.xml"',
        f"      resource 0x7f100001 {policy.PACKAGE}:xml/{file_paths_name}: t=0x03 d=0x00000000",
        f'        (string8) "res/xml/{file_paths_name}.xml"',
        f"      resource 0x7f100002 {policy.PACKAGE}:xml/{network_name}: t=0x03 d=0x00000000",
        f'        (string8) "res/xml/{network_value_name}.xml"',
    ])
    if mutation in {"network-qualified-resource", "extraction-qualified-resource", "file-qualified-resource"}:
        resource_id = {
            "network-qualified-resource": "0x7f100002",
            "extraction-qualified-resource": "0x7f100000",
            "file-qualified-resource": "0x7f100001",
        }[mutation]
        resource_name = {
            "network-qualified-resource": "network_security_config",
            "extraction-qualified-resource": "data_extraction_rules",
            "file-qualified-resource": "file_paths",
        }[mutation]
        resource_lines.extend([
            "    config (v31):",
            f"      resource {resource_id} {policy.PACKAGE}:xml/{resource_name}: t=0x03 d=0x00000000",
            f'        (string8) "res/xml/{resource_name}.xml"',
        ])
    resources.write_text("\n".join(resource_lines) + "\n", encoding="utf-8")
    command = [
        str(MANIFEST_VERIFIER), str(manifest), str(resources), str(network), str(extraction),
        str(file_paths_policy), policy.PACKAGE, "1.0.0", "10000", commit, backend,
        "APPROVED", "3",
    ]
    return command, manifest


def test_release_merged_manifest_policy_accepts_exact_current_contract(tmp_path: Path):
    command, _ = _manifest_policy_fixture(tmp_path)
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    assert result.returncode == 0, result.stderr
    assert re.fullmatch(r"[0-9a-f]{64}\n", result.stdout)


@pytest.mark.parametrize(
    "mutation",
    [
        "sdk", "backup", "cleartext", "permission", "exported", "network", "deeplink",
        "duplicate-metadata", "resource-confusion", "file-resource-confusion", "root-path",
        "provider-authority", "grant-uri", "foreground-service", "intent-attribute",
        "component-metadata", "network-qualified-resource", "extraction-qualified-resource",
        "file-qualified-resource", "network-concrete-path",
    ],
)
def test_release_merged_manifest_policy_rejects_security_drift(tmp_path: Path, mutation: str):
    command, _ = _manifest_policy_fixture(tmp_path, mutation)
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    assert result.returncode != 0
    assert "manifest rejected" in result.stderr


def test_gpg_secret_operations_ignore_persistent_configuration_and_agent():
    assert "gpg.conf common.conf gpg-agent.conf dirmngr.conf" in SOURCE
    assert 'SECRET_GNUPGHOME="$TEMP_ROOT/isolated-secret-gpg"' in SOURCE
    assert SOURCE.count("--no-options") >= 10
    assert SOURCE.count('--agent-program "$GPG_AGENT"') >= 6
    assert '"$GPGCONF" --homedir "$SECRET_GNUPGHOME" --kill gpg-agent' in SOURCE


def test_every_governance_caller_fetches_fresh_repository_security_state():
    rules = SOURCE.split("verify_rulesets() {", 1)[1].split("\n}", 1)[0]
    assert rules.index('github_get "/repos/$REPOSITORY" "$TEMP_ROOT/repository.json"') < rules.index(
        ".security_and_analysis.secret_scanning.status"
    )
    assert "verify_rulesets" in SOURCE.split("create_signed_release_tag()", 1)[1]


def test_owner_ceremony_paginates_tags_and_drafts_with_fixed_fail_closed_bounds():
    assert "--paginate --slurp" not in SOURCE
    assert 'while (( page <= 1000 )); do' in SOURCE
    assert 'while (( release_page <= 1000 )); do' in SOURCE
    assert 'repository tag history exceeds the fixed 1000-page release bound' in SOURCE
    assert 'repository release history exceeds the fixed 1000-page release bound' in SOURCE


def test_actions_zip_and_release_asset_downloads_use_their_fixed_compatible_media_types():
    assert "unallowlisted authenticated GitHub download media type" in SOURCE
    assert "'application/vnd.github+json'" in SOURCE.split("actions/artifacts/$RESOLVED_ACTIONS_ARTIFACT_ID/zip", 1)[1]
    assert "'application/octet-stream'" in SOURCE.split("releases/assets/$asset_id", 1)[1]


def test_bounded_actions_zip_download_uses_json_media_type_and_rejects_an_unlisted_one(
    tmp_path: Path,
):
    """Exercise the helper's actual gh invocation without any network access."""
    helper = SOURCE.split("github_download_bounded() {", 1)[1].split("\n}\n\nverify_monotonic", 1)[0]
    fake_gh = tmp_path / "gh"
    fake_gh.write_text(
        "#!/bin/bash\n"
        "case \"$*\" in\n"
        "  *'Accept: application/vnd.github+json'*'/actions/artifacts/'*) printf zip-bytes ;;\n"
        "  *) exit 91 ;;\n"
        "esac\n",
        encoding="utf-8",
    )
    fake_gh.chmod(0o700)
    output = tmp_path / "artifact.zip"
    program = (
        "fail() { return 1; }\n"
        "require_regular_private_file() { test -s \"$1\"; }\n"
        "github_download_bounded() {" + helper + "\n}\n"
        "github_download_bounded '/repos/x/actions/artifacts/1/zip' \"$1\" 1024 "
        "\"$2\"\n"
    )
    environment = {"GH": str(fake_gh), "PYTHON": sys.executable}
    accepted = subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", "-c", program, "download-test", str(output),
         "application/vnd.github+json"],
        text=True,
        capture_output=True,
        env=environment,
        check=False,
    )
    assert accepted.returncode == 0, accepted.stderr
    assert output.read_bytes() == b"zip-bytes"
    rejected = subprocess.run(
        ["/bin/bash", "--noprofile", "--norc", "-c", program, "download-test", str(tmp_path / "bad"),
         "application/not-allowed"],
        text=True,
        capture_output=True,
        env=environment,
        check=False,
    )
    assert rejected.returncode != 0


def test_apkanalyzer_transitive_classpath_tamper_is_rejected_before_java_exec(tmp_path: Path):
    executor = ROOT / "scripts/patrolgrid_apkanalyzer.py"
    lib = tmp_path / "lib"
    dependency = lib / "deps/transitive.jar"
    dependency.parent.mkdir(parents=True)
    dependency.write_bytes(b"reviewed dependency")
    classpath = lib / "apkanalyzer-classpath.jar"
    with zipfile.ZipFile(classpath, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nClass-Path: deps/transitive.jar\r\n\r\n")
    import hashlib

    manifest = tmp_path / "classpath.sha256"
    manifest.write_text(f"{hashlib.sha256(dependency.read_bytes()).hexdigest()}  deps/transitive.jar\n")
    java = tmp_path / "java"
    java.write_text("#!/bin/sh\nexit 0\n")
    java.chmod(0o700)
    build_tools = tmp_path / "build-tools/36.0.0"
    (build_tools / "lib64").mkdir(parents=True)
    fixture_build_files = {
        build_tools / "aapt": b"fixture aapt",
        build_tools / "lib64/libc++.dylib": b"fixture libc++",
        build_tools / "source.properties": b"Pkg.Revision=36.0.0\n",
    }
    for path, content in fixture_build_files.items():
        path.write_bytes(content)
    command = [str(executor), str(lib), str(classpath), str(manifest), str(java),
               str(build_tools), str(tmp_path / "stage"), "--", "manifest", "print", "x.apk"]
    # The fixture's classpath JAR uses a fixture digest, so make a local copy of
    # the executor's fixed root-JAR check for this behavioral transitive test.
    source = executor.read_text(encoding="utf-8").replace(
        '"6569cf37ed9481aac7b3f6f563fd6cfbe46395dd2d59885ee1174dba9bad063a"',
        repr(hashlib.sha256(classpath.read_bytes()).hexdigest()),
    )
    for production_digest, fixture_path in (
        ("170717682f714712c5b6854af73cfe37aeda342ff422384e98d67fc1b490f49b", build_tools / "aapt"),
        ("834cf92eead41eb0c9368604e5ccf1e17b228ce8169d44583cebfaf779f6d27e", build_tools / "lib64/libc++.dylib"),
        ("7dee6632e9ad6cb111da2bb99d747211e27927061b1276d040bb1d71fded5ebb", build_tools / "source.properties"),
    ):
        source = source.replace(production_digest, hashlib.sha256(fixture_path.read_bytes()).hexdigest())
    fixture_executor = tmp_path / "executor.py"
    fixture_executor.write_text(source, encoding="utf-8")
    fixture_executor.chmod(0o700)
    accepted = subprocess.run([sys.executable, "-I", str(fixture_executor), *command[1:]], text=True, capture_output=True)
    assert accepted.returncode == 0, accepted.stderr
    dependency.write_bytes(b"tampered dependency")
    tampered_command = [str(lib), str(classpath), str(manifest), str(java), str(build_tools),
                        str(tmp_path / "stage-tampered"), "--", "manifest", "print", "x.apk"]
    rejected = subprocess.run([sys.executable, "-I", str(fixture_executor), *tampered_command], text=True, capture_output=True)
    assert rejected.returncode != 0
    assert "digest changed" in rejected.stderr


@pytest.mark.skipif(sys.platform != "darwin", reason="real pinned macOS apkanalyzer graph")
def test_real_pinned_apkanalyzer_graph_executes_manifest_policy_path(tmp_path: Path):
    sdk = Path("/Users/sujithsampath/Library/Android/sdk")
    build_tools = sdk / "build-tools/36.0.0"
    aapt = build_tools / "aapt"
    android_jar = sdk / "platforms/android-36/android.jar"
    classpath = sdk / "cmdline-tools/latest/lib/apkanalyzer-classpath.jar"
    java = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java")
    if not all(path.is_file() for path in (aapt, android_jar, classpath, java)):
        pytest.skip("pinned SDK 36/JBR files are unavailable")

    manifest = tmp_path / "AndroidManifest.xml"
    manifest.write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="com.example.fixture"><uses-sdk android:minSdkVersion="26" '
        'android:targetSdkVersion="36"/><application android:debuggable="false"/></manifest>\n',
        encoding="utf-8",
    )
    apk = tmp_path / "fixture.apk"
    packaged = subprocess.run(
        [aapt, "package", "-f", "-M", manifest, "-I", android_jar, "-F", apk],
        text=True,
        capture_output=True,
        check=False,
    )
    assert packaged.returncode == 0, packaged.stderr
    result = subprocess.run(
        [
            "/usr/bin/python3", "-I", ROOT / "scripts/patrolgrid_apkanalyzer.py",
            sdk / "cmdline-tools/latest/lib", classpath,
            ROOT / "release/patrolgrid-apkanalyzer-classpath.sha256", java,
            build_tools, tmp_path / "verified-graph", "--", "manifest", "debuggable", apk,
        ],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "false"


def _tool(name: str, *fixed: str) -> str | None:
    for path in fixed:
        if Path(path).is_file() and os.access(path, os.X_OK):
            return path
    return shutil.which(name)


def test_apksigner_binds_embedded_release_metadata_inside_apk(tmp_path: Path):
    """Proves embedded metadata is covered by a real APK signature."""
    sdk = Path(os.environ.get("ANDROID_HOME", "/Users/sujithsampath/Library/Android/sdk"))
    apksigner = _tool("apksigner", str(sdk / "build-tools/36.0.0/apksigner"))
    zipalign = _tool("zipalign", str(sdk / "build-tools/36.0.0/zipalign"))
    aapt = _tool("aapt", str(sdk / "build-tools/36.0.0/aapt"))
    keytool = _tool(
        "keytool",
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool",
    )
    android_jar = sdk / "platforms/android-36/android.jar"
    if not all((apksigner, zipalign, aapt, keytool)) or not android_jar.is_file():
        pytest.skip("real SDK 36.0.0/JDK signing tools are unavailable")

    password = "fixture-only-password-1234"
    keystore = tmp_path / "fixture.p12"
    signed_result = subprocess.run(
        [
            keytool,
            "-genkeypair",
            "-alias",
            "patrolgrid",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "2",
            "-dname",
            "CN=Fixture",
            "-keystore",
            str(keystore),
            "-storetype",
            "PKCS12",
            "-storepass",
            password,
        ],
        capture_output=True,
        check=False,
    )
    assert signed_result.returncode == 0, signed_result.stdout + signed_result.stderr
    manifest = tmp_path / "AndroidManifest.xml"
    manifest.write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="com.example.fixture"><uses-sdk android:minSdkVersion="26" '
        'android:targetSdkVersion="36"/><application android:debuggable="false"/></manifest>\n',
        encoding="utf-8",
    )
    unsigned = tmp_path / "unsigned.zip"
    subprocess.run(
        [aapt, "package", "-f", "-M", manifest, "-I", android_jar, "-F", unsigned],
        capture_output=True,
        check=True,
    )
    metadata_name = "assets/patrolgrid-release.json"
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_STORED) as bundle:
        bundle.writestr(metadata_name, '{"commit":"' + "a" * 40 + '"}\n')
    aligned = tmp_path / "aligned.zip"
    signed = tmp_path / "signed.apk"
    subprocess.run([zipalign, "-P", "16", "-f", "4", unsigned, aligned], check=True)
    signing_env = {**os.environ, "JAVA_HOME": str(Path(keytool).parents[1])}
    sign_result = subprocess.run(
        [
            apksigner,
            "sign",
            "--min-sdk-version",
            "26",
            "--ks",
            str(keystore),
            "--ks-type",
            "PKCS12",
            "--ks-key-alias",
            "patrolgrid",
            "--ks-pass",
            "stdin",
            "--key-pass",
            "stdin",
            "--v1-signing-enabled",
            "false",
            "--v2-signing-enabled",
            "true",
            "--v3-signing-enabled",
            "true",
            "--v4-signing-enabled",
            "false",
            "--out",
            str(signed),
            str(aligned),
        ],
        input=f"{password}\n{password}\n",
        text=True,
        capture_output=True,
        check=False,
        env=signing_env,
    )
    assert sign_result.returncode == 0, sign_result.stdout + sign_result.stderr
    verified = subprocess.run(
        [apksigner, "verify", "--min-sdk-version", "26", "--print-certs", signed],
        text=True,
        capture_output=True,
        check=False,
        env=signing_env,
    )
    assert verified.returncode == 0, verified.stdout + verified.stderr
    assert "certificate SHA-256 digest" in verified.stdout
    with zipfile.ZipFile(signed) as bundle:
        assert bundle.namelist().count(metadata_name) == 1
        assert bundle.read(metadata_name).startswith(b'{"commit"')
