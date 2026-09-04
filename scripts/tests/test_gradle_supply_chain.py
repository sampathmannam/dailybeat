import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android"
ROOT_BUILD = (ANDROID / "build.gradle.kts").read_text(encoding="utf-8")
APP_BUILD = (ANDROID / "app/build.gradle.kts").read_text(encoding="utf-8")
LOCKFILE = (ANDROID / "app/gradle.lockfile").read_text(encoding="utf-8")
VERIFICATION_FILE = ANDROID / "gradle/verification-metadata.xml"
WORKFLOWS = [
    ROOT / ".github/workflows/ci.yml",
    ROOT / ".github/workflows/security.yml",
]
NAMESPACE = {"v": "https://schema.gradle.org/dependency-verification"}


def test_gradle_uses_strict_locking_for_every_project_configuration():
    dependency_locking = ROOT_BUILD.split("allprojects {", maxsplit=1)[1]

    assert "dependencyLocking {" in dependency_locking
    assert "lockAllConfigurations()" in dependency_locking
    assert "lockMode.set(LockMode.STRICT)" in dependency_locking
    assert "LockMode.LENIENT" not in ROOT_BUILD
    assert "LockMode.DEFAULT" not in ROOT_BUILD


def test_android_buildscript_forces_patched_bouncycastle_tooling_components():
    buildscript = ROOT_BUILD.split("buildscript {", maxsplit=1)[1].split(
        "plugins {", maxsplit=1
    )[0]

    assert "configurations.classpath" in buildscript
    assert "resolutionStrategy.force(" in buildscript
    for module in ("bcpkix-jdk18on", "bcprov-jdk18on", "bcutil-jdk18on"):
        assert f'"org.bouncycastle:{module}:1.84"' in buildscript
    assert "1.77" not in buildscript

    assert '"com.google.protobuf" -> useVersion("3.25.5")' in buildscript
    assert '"io.netty" -> useVersion("4.1.136.Final")' in buildscript
    assert "3.24.4" not in buildscript
    assert "4.1.110.Final" not in buildscript


def test_lockfile_covers_direct_dependencies_and_all_shipped_variants():
    assert LOCKFILE.startswith("# This is a Gradle generated file")
    assert "empty=" in LOCKFILE

    direct_coordinates = set(
        re.findall(
            r'(?:implementation|debugImplementation|testImplementation|androidTestImplementation|kapt)\('
            r'"([^":]+:[^":]+):[^"$]+"\)',
            APP_BUILD,
        )
    )
    locked_coordinates = {
        line.split("=", maxsplit=1)[0].rsplit(":", maxsplit=1)[0]
        for line in LOCKFILE.splitlines()
        if line and not line.startswith("#") and not line.startswith("empty=")
    }

    assert direct_coordinates <= locked_coordinates
    for configuration in (
        "debugCompileClasspath",
        "debugRuntimeClasspath",
        "debugUnitTestRuntimeClasspath",
        "debugAndroidTestRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeClasspath",
        "releaseUnitTestRuntimeClasspath",
    ):
        assert configuration in LOCKFILE

    # Gradle 8.11's lock writer omits this inherited Kotlin component from the
    # Android-test runtime entry even though lint resolves it there. Keep the
    # explicit state so a clean strict lint build cannot silently unlock it.
    assert any(
        line.startswith("org.jetbrains.kotlin:kotlin-stdlib-common:")
        and "debugAndroidTestRuntimeClasspath"
        in line.split("=", maxsplit=1)[1].split(",")
        for line in LOCKFILE.splitlines()
    )


def test_scanner_flagged_dependencies_stay_out_of_application_runtime():
    locked = {
        line.split("=", maxsplit=1)[0]: set(line.split("=", maxsplit=1)[1].split(","))
        for line in LOCKFILE.splitlines()
        if line and not line.startswith("#") and not line.startswith("empty=")
    }

    bcprov = {
        coordinate: configurations
        for coordinate, configurations in locked.items()
        if coordinate.startswith("org.bouncycastle:bcprov-jdk18on:")
    }
    assert set(bcprov) == {"org.bouncycastle:bcprov-jdk18on:1.84"}
    assert all(
        "UnitTest" in configuration
        or configuration == "testImplementationDependenciesMetadata"
        for configuration in next(iter(bcprov.values()))
    )

    internal_tool_prefixes = ("com.google.protobuf:", "io.netty:")
    internal_tooling = {
        coordinate: configurations
        for coordinate, configurations in locked.items()
        if coordinate.startswith(internal_tool_prefixes)
    }
    assert internal_tooling
    for configurations in internal_tooling.values():
        assert configurations
        assert all(
            configuration.startswith("_internal-unified-test-platform-")
            for configuration in configurations
        )


def test_unified_test_platform_security_overrides_are_isolated_and_patched():
    allprojects = ROOT_BUILD.split("allprojects {", maxsplit=1)[1]
    override = allprojects.split("dependencyLocking {", maxsplit=1)[0]

    assert 'name.startsWith("_internal-unified-test-platform-")' in override
    assert '"com.google.protobuf" -> useVersion("3.25.5")' in override
    assert '"io.netty" -> useVersion("4.1.136.Final")' in override
    assert "resolutionStrategy.force" not in override

    for line in LOCKFILE.splitlines():
        if line.startswith("com.google.protobuf:"):
            assert ":3.25.5=" in line
        if line.startswith("io.netty:"):
            assert ":4.1.136.Final=" in line


def test_verification_metadata_has_sha256_for_every_artifact_and_no_bypasses():
    root = ET.parse(VERIFICATION_FILE).getroot()
    configuration = root.find("v:configuration", NAMESPACE)
    assert configuration is not None
    assert configuration.findtext("v:verify-metadata", namespaces=NAMESPACE) == "true"

    # The Android graph contains repositories/artifacts without a reliable common
    # signing-key chain, so strict SHA-256 verification is the portable baseline.
    assert configuration.findtext("v:verify-signatures", namespaces=NAMESPACE) == "false"
    for bypass in (
        "v:trusted-artifacts",
        "v:ignored-keys",
        "v:trusted-keys",
    ):
        assert configuration.find(bypass, NAMESPACE) is None

    artifacts = root.findall("./v:components/v:component/v:artifact", NAMESPACE)
    assert artifacts
    for artifact in artifacts:
        checksums = artifact.findall("v:sha256", NAMESPACE)
        assert checksums
        assert not artifact.findall("v:md5", NAMESPACE)
        assert not artifact.findall("v:sha1", NAMESPACE)
        for checksum in checksums:
            assert re.fullmatch(r"[0-9a-f]{64}", checksum.attrib["value"])

    component_versions = {
        (component.attrib["group"], component.attrib["name"], component.attrib["version"])
        for component in root.findall("./v:components/v:component", NAMESPACE)
    }
    protobuf = {
        version for group, _name, version in component_versions
        if group == "com.google.protobuf"
    }
    netty = {
        version for group, _name, version in component_versions
        if group == "io.netty"
    }
    assert protobuf == {"3.25.5"}
    assert netty == {"4.1.136.Final"}


def test_verification_metadata_covers_clean_cache_metadata_variants():
    root = ET.parse(VERIFICATION_FILE).getroot()
    components = {
        (component.attrib["group"], component.attrib["name"], component.attrib["version"]): {
            artifact.attrib["name"]: artifact.find("v:sha256", NAMESPACE).attrib["value"]
            for artifact in component.findall("v:artifact", NAMESPACE)
        }
        for component in root.findall("./v:components/v:component", NAMESPACE)
    }

    expected = {
        ("com.google.guava", "guava-parent", "33.3.1-jre"): {
            "guava-parent-33.3.1-jre.pom":
                "55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287",
        },
        ("org.junit", "junit-bom", "5.10.2"): {
            "junit-bom-5.10.2.module":
                "de23b114b3e4119a8fe6eb17bed5a3852816698bace67071579d6d927ebb080a",
            "junit-bom-5.10.2.pom":
                "169dd904a4b0f6520cffe658cc62292bfe9f3c14a989fa92120724cde43a9968",
        },
        ("org.junit", "junit-bom", "5.9.2"): {
            "junit-bom-5.9.2.module":
                "ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f",
            "junit-bom-5.9.2.pom":
                "2ed07d65845131f5336a86476c9a4056b59d0b58b9815ab3679bb0f36f35f705",
        },
        ("com.android.tools.build", "aapt2", "8.10.1-12782657"): {
            "aapt2-8.10.1-12782657-linux.jar":
                "52f864b7fd20a9ff09fc3db96162537a63c5b38ecc1c2549db4b491c6a517ff0",
            "aapt2-8.10.1-12782657-osx.jar":
                "91fb66f4999d114c38713bb4429451e4f2bbe6cfd7c1931e43ee13f52234d426",
        },
    }
    for component, artifacts in expected.items():
        assert artifacts.items() <= components[component].items()


def test_ci_and_security_gradle_commands_enforce_strict_verification():
    for workflow_path in WORKFLOWS:
        workflow = workflow_path.read_text(encoding="utf-8")
        gradle_commands = [
            line
            for line in workflow.splitlines()
            if "./gradlew" in line and not line.lstrip().startswith("#")
        ]
        assert gradle_commands
        assert all("--dependency-verification strict" in line for line in gradle_commands)
        assert "--dependency-verification off" not in workflow
        assert "--dependency-verification lenient" not in workflow
        assert "--write-verification-metadata" not in workflow
        assert "--write-locks" not in workflow


def test_ci_android_graph_is_always_resolved_from_a_cold_cache():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    build_job = workflow.split("  build:\n", maxsplit=1)[1]

    assert build_job.count(
        "GRADLE_USER_HOME: ${{ runner.temp }}/patrolgrid-gradle-cold"
    ) == 3
    assert "cache-disabled: true" in build_job
    assert "--refresh-dependencies --dependency-verification strict" in build_job


def test_ci_enables_kvm_before_starting_the_android_emulator():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    build_job = workflow.split("  build:\n", maxsplit=1)[1]

    kvm_step = "      - name: Enable KVM acceleration\n"
    emulator_step = "      - name: Instrumentation tests (emulator)\n"
    assert build_job.index(kvm_step) < build_job.index(emulator_step)
    kvm_preflight = build_job.split(kvm_step, maxsplit=1)[1].split(
        emulator_step, maxsplit=1
    )[0]
    assert "set -euxo pipefail" in kvm_preflight
    assert "if [[ ! -c /dev/kvm ]]; then" in kvm_preflight
    assert "grep -qw vmx /proc/cpuinfo" in kvm_preflight
    assert "kvm_vendor_module=kvm_intel" in kvm_preflight
    assert "grep -qw svm /proc/cpuinfo" in kvm_preflight
    assert "kvm_vendor_module=kvm_amd" in kvm_preflight
    assert "::error::Runner CPU exposes neither Intel VMX nor AMD SVM" in kvm_preflight
    assert "sudo modprobe kvm" in kvm_preflight
    assert 'sudo modprobe "$kvm_vendor_module"' in kvm_preflight
    assert "refusing software-emulated Android tests" in kvm_preflight
    assert 'KERNEL=="kvm", GROUP="kvm", MODE="0666"' in kvm_preflight
    assert "sudo udevadm control --reload-rules" in kvm_preflight
    assert "sudo udevadm trigger --name-match=kvm" in kvm_preflight
    assert "sudo chmod 0666 /dev/kvm" in kvm_preflight
    assert "stat -c 'KVM device:" in kvm_preflight
    assert "test -c /dev/kvm" in kvm_preflight
    assert "test -r /dev/kvm" in kvm_preflight
    assert "test -w /dev/kvm" in kvm_preflight
    assert "disable-linux-hw-accel: false" in build_job
    assert "-accel off" not in build_job


def test_gradle_wrapper_distribution_is_pinned_and_validated():
    wrapper_properties = (
        ANDROID / "gradle/wrapper/gradle-wrapper.properties"
    ).read_text(encoding="utf-8")

    checksum_match = re.search(
        r"^distributionSha256Sum=([0-9a-f]{64})$",
        wrapper_properties,
        flags=re.MULTILINE,
    )
    assert checksum_match
    assert "validateDistributionUrl=true" in wrapper_properties

    for workflow_path in WORKFLOWS:
        workflow = workflow_path.read_text(encoding="utf-8")
        assert "gradle/actions/wrapper-validation@" in workflow
