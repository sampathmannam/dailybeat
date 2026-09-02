#!/usr/bin/python3 -I
"""Fail-closed policy for the merged manifest of a PatrolGrid release APK."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import urllib.parse
import xml.etree.ElementTree as ET


ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.dailybeat.app.patrolgrid"
PERMISSIONS = {
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_LOCATION",
    "android.permission.HIDE_OVERLAY_WINDOWS",
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",
    f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
EXPORTED_COMPONENTS = {
    ("activity", "com.dailybeat.app.MainActivity", ""),
    ("service", "androidx.work.impl.background.systemjob.SystemJobService",
     "android.permission.BIND_JOB_SERVICE"),
    ("receiver", "androidx.work.impl.diagnostics.DiagnosticsReceiver",
     "android.permission.DUMP"),
    ("receiver", "androidx.profileinstaller.ProfileInstallReceiver",
     "android.permission.DUMP"),
}
COMPONENTS = {
    "activity": {
        "com.dailybeat.app.MainActivity",
        "com.google.android.gms.common.api.GoogleApiActivity",
    },
    "activity-alias": set(),
    "service": {
        "com.dailybeat.app.capture.LocationService",
        "androidx.room.MultiInstanceInvalidationService",
        "androidx.work.impl.background.systemalarm.SystemAlarmService",
        "androidx.work.impl.background.systemjob.SystemJobService",
        "androidx.work.impl.foreground.SystemForegroundService",
    },
    "receiver": {
        "com.dailybeat.app.notify.BootReceiver",
        "com.dailybeat.app.notify.DailyReminderReceiver",
        "com.dailybeat.app.notify.MiddayPulseReceiver",
        "androidx.profileinstaller.ProfileInstallReceiver",
        "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy",
        "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy",
        "androidx.work.impl.background.systemalarm.ConstraintProxy$NetworkStateProxy",
        "androidx.work.impl.background.systemalarm.ConstraintProxy$StorageNotLowProxy",
        "androidx.work.impl.background.systemalarm.ConstraintProxyUpdateReceiver",
        "androidx.work.impl.background.systemalarm.RescheduleReceiver",
        "androidx.work.impl.diagnostics.DiagnosticsReceiver",
        "androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver",
    },
    "provider": {
        "androidx.core.content.FileProvider",
        "androidx.startup.InitializationProvider",
    },
}
RESOURCE_REFERENCE = "<resource-reference>"
APPLICATION_ATTRIBUTES = {
    "theme": RESOURCE_REFERENCE,
    "label": RESOURCE_REFERENCE,
    "icon": RESOURCE_REFERENCE,
    "name": "com.dailybeat.app.DailyBeatApp",
    "allowBackup": "false",
    "supportsRtl": "true",
    "extractNativeLibs": "false",
    "fullBackupContent": "false",
    "usesCleartextTraffic": "false",
    "networkSecurityConfig": RESOURCE_REFERENCE,
    "roundIcon": RESOURCE_REFERENCE,
    "appComponentFactory": "androidx.core.app.CoreComponentFactory",
    "dataExtractionRules": RESOURCE_REFERENCE,
}
COMPONENT_ATTRIBUTES = {
    "androidx.core.content.FileProvider": {
        "name": "androidx.core.content.FileProvider",
        "exported": "false",
        "authorities": f"{PACKAGE}.fileprovider",
        "grantUriPermissions": "true",
    },
    "com.dailybeat.app.MainActivity": {
        "theme": RESOURCE_REFERENCE,
        "name": "com.dailybeat.app.MainActivity",
        "exported": "true",
    },
    "com.dailybeat.app.notify.DailyReminderReceiver": {
        "name": "com.dailybeat.app.notify.DailyReminderReceiver",
        "exported": "false",
    },
    "com.dailybeat.app.notify.BootReceiver": {
        "name": "com.dailybeat.app.notify.BootReceiver",
        "exported": "false",
    },
    "com.dailybeat.app.notify.MiddayPulseReceiver": {
        "name": "com.dailybeat.app.notify.MiddayPulseReceiver",
        "exported": "false",
    },
    "com.dailybeat.app.capture.LocationService": {
        "name": "com.dailybeat.app.capture.LocationService",
        "exported": "false",
        "foregroundServiceType": "0x8",
    },
    "com.google.android.gms.common.api.GoogleApiActivity": {
        "theme": RESOURCE_REFERENCE,
        "name": "com.google.android.gms.common.api.GoogleApiActivity",
        "exported": "false",
    },
    "androidx.startup.InitializationProvider": {
        "name": "androidx.startup.InitializationProvider",
        "exported": "false",
        "authorities": f"{PACKAGE}.androidx-startup",
    },
    "androidx.work.impl.background.systemalarm.SystemAlarmService": {
        "name": "androidx.work.impl.background.systemalarm.SystemAlarmService",
        "enabled": RESOURCE_REFERENCE,
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemjob.SystemJobService": {
        "name": "androidx.work.impl.background.systemjob.SystemJobService",
        "permission": "android.permission.BIND_JOB_SERVICE",
        "enabled": RESOURCE_REFERENCE,
        "exported": "true",
        "directBootAware": "false",
    },
    "androidx.work.impl.foreground.SystemForegroundService": {
        "name": "androidx.work.impl.foreground.SystemForegroundService",
        "enabled": RESOURCE_REFERENCE,
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver": {
        "name": "androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver",
        "enabled": "true",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy": {
        "name": "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy",
        "enabled": "false",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy": {
        "name": "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy",
        "enabled": "false",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.ConstraintProxy$StorageNotLowProxy": {
        "name": "androidx.work.impl.background.systemalarm.ConstraintProxy$StorageNotLowProxy",
        "enabled": "false",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.ConstraintProxy$NetworkStateProxy": {
        "name": "androidx.work.impl.background.systemalarm.ConstraintProxy$NetworkStateProxy",
        "enabled": "false",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.RescheduleReceiver": {
        "name": "androidx.work.impl.background.systemalarm.RescheduleReceiver",
        "enabled": "false",
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.background.systemalarm.ConstraintProxyUpdateReceiver": {
        "name": "androidx.work.impl.background.systemalarm.ConstraintProxyUpdateReceiver",
        "enabled": RESOURCE_REFERENCE,
        "exported": "false",
        "directBootAware": "false",
    },
    "androidx.work.impl.diagnostics.DiagnosticsReceiver": {
        "name": "androidx.work.impl.diagnostics.DiagnosticsReceiver",
        "permission": "android.permission.DUMP",
        "enabled": "true",
        "exported": "true",
        "directBootAware": "false",
    },
    "androidx.room.MultiInstanceInvalidationService": {
        "name": "androidx.room.MultiInstanceInvalidationService",
        "exported": "false",
        "directBootAware": "true",
    },
    "androidx.profileinstaller.ProfileInstallReceiver": {
        "name": "androidx.profileinstaller.ProfileInstallReceiver",
        "permission": "android.permission.DUMP",
        "enabled": "true",
        "exported": "true",
        "directBootAware": "false",
    },
}
INTENT_FILTERS = {
    "com.dailybeat.app.MainActivity": [
        (("android.intent.action.MAIN",), ("android.intent.category.LAUNCHER",)),
    ],
    "com.dailybeat.app.notify.BootReceiver": [
        (("android.intent.action.BOOT_COMPLETED",), ()),
    ],
    "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy": [
        (("android.intent.action.ACTION_POWER_CONNECTED",
          "android.intent.action.ACTION_POWER_DISCONNECTED"), ()),
    ],
    "androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy": [
        (("android.intent.action.BATTERY_OKAY", "android.intent.action.BATTERY_LOW"), ()),
    ],
    "androidx.work.impl.background.systemalarm.ConstraintProxy$StorageNotLowProxy": [
        (("android.intent.action.DEVICE_STORAGE_LOW", "android.intent.action.DEVICE_STORAGE_OK"), ()),
    ],
    "androidx.work.impl.background.systemalarm.ConstraintProxy$NetworkStateProxy": [
        (("android.net.conn.CONNECTIVITY_CHANGE",), ()),
    ],
    "androidx.work.impl.background.systemalarm.RescheduleReceiver": [
        (("android.intent.action.BOOT_COMPLETED", "android.intent.action.TIME_SET",
          "android.intent.action.TIMEZONE_CHANGED"), ()),
    ],
    "androidx.work.impl.background.systemalarm.ConstraintProxyUpdateReceiver": [
        (("androidx.work.impl.background.systemalarm.UpdateProxies",), ()),
    ],
    "androidx.work.impl.diagnostics.DiagnosticsReceiver": [
        (("androidx.work.diagnostics.REQUEST_DIAGNOSTICS",), ()),
    ],
    "androidx.profileinstaller.ProfileInstallReceiver": [
        (("androidx.profileinstaller.action.INSTALL_PROFILE",), ()),
        (("androidx.profileinstaller.action.SKIP_FILE",), ()),
        (("androidx.profileinstaller.action.SAVE_PROFILE",), ()),
        (("androidx.profileinstaller.action.BENCHMARK_OPERATION",), ()),
    ],
}
NETWORK_POLICY = """E: network-security-config
  E: base-config
    A: cleartextTrafficPermitted=(type 0x12)0x0
    E: trust-anchors
      E: certificates
        A: src="system" (Raw: "system")
"""
EXTRACTION_POLICY = """E: data-extraction-rules
  E: cloud-backup
    E: exclude
      A: domain="root" (Raw: "root")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="file" (Raw: "file")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="database" (Raw: "database")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="sharedpref" (Raw: "sharedpref")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="external" (Raw: "external")
      A: path="." (Raw: ".")
  E: device-transfer
    E: exclude
      A: domain="root" (Raw: "root")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="file" (Raw: "file")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="database" (Raw: "database")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="sharedpref" (Raw: "sharedpref")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="external" (Raw: "external")
      A: path="." (Raw: ".")
"""
FILE_PATHS_POLICY = """E: paths
  E: external-files-path
    A: name="dailybeat_pdfs" (Raw: "dailybeat_pdfs")
    A: path="DailyBeat/" (Raw: "DailyBeat/")
"""


def stop(message: str) -> None:
    raise SystemExit(f"PatrolGrid release manifest rejected: {message}")


def attribute(element: ET.Element, name: str) -> str:
    return element.get(ANDROID + name, "")


def android_attributes(element: ET.Element, context: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for key, value in element.attrib.items():
        if not key.startswith(ANDROID):
            stop(f"{context} carries a non-Android attribute")
        result[key[len(ANDROID):]] = value
    return result


def verify_attributes(
    element: ET.Element,
    expected: dict[str, str],
    context: str,
    optional: dict[str, str] | None = None,
) -> None:
    optional = optional or {}
    observed = android_attributes(element, context)
    if set(observed) - (set(expected) | set(optional)) or set(expected) - set(observed):
        stop(f"{context} attribute allowlist changed")
    for name, expected_value in {**expected, **optional}.items():
        if name not in observed:
            continue
        if expected_value == RESOURCE_REFERENCE:
            if not re.fullmatch(r"@ref/0x[0-9a-f]+", observed[name]):
                stop(f"{context} {name} is not a compiled resource reference")
        elif observed[name] != expected_value:
            stop(f"{context} {name} changed")


def resource_table(path: Path) -> tuple[dict[str, str], dict[str, list[tuple[str, str]]]]:
    """Parse the resource specs and every concrete configuration value.

    A manifest reference is not safe merely because its *default* XML is safe:
    Android can select a qualified (for example ``xml-v31``) value at runtime.
    Keep the aapt dump as an explicit binding witness and require a single,
    unqualified value for each release-policy XML resource below.
    """
    result: dict[str, str] = {}
    configurations: dict[str, list[tuple[str, str]]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(
            r"\s*spec resource (0x[0-9a-fA-F]+) ([^\s:]+:[^\s]+): flags=0x[0-9a-fA-F]+",
            line,
        )
        if not match:
            continue
        resource_id, name = match.groups()
        resource_id = resource_id.lower()
        if resource_id in result:
            stop(f"compiled resource id is duplicated: {resource_id}")
        result[resource_id] = name
    # A concrete resource line inherits its immediately preceding config; the
    # following string8 path is emitted by ``aapt dump --values resources``.
    active_resource = None
    active_configuration = None
    for line in path.read_text(encoding="utf-8").splitlines():
        config_match = re.fullmatch(r"\s*config \(([^)]*)\):\s*", line)
        if config_match:
            active_configuration = config_match.group(1)
            active_resource = None
            continue
        value_match = re.fullmatch(r"\s*resource (0x[0-9a-fA-F]+) ([^\s:]+:[^\s]+):.*", line)
        if value_match:
            resource_id, full_name = value_match.groups()
            resource_id = resource_id.lower()
            if active_configuration is None or result.get(resource_id) != full_name:
                stop("compiled resource value does not match its specification")
            active_resource = (resource_id, active_configuration)
            continue
        string_match = re.fullmatch(r'\s*\(string8\) "([^"]+)"', line)
        if string_match and active_resource is not None:
            resource_id, configuration = active_resource
            configurations.setdefault(resource_id, []).append((configuration, string_match.group(1)))
            active_resource = None
    if not result:
        stop("compiled resource table could not be parsed")
    return result, configurations


def require_xml_resource(
    reference: str,
    expected_name: str,
    resources: dict[str, str],
    configurations: dict[str, list[tuple[str, str]]],
    context: str,
) -> str:
    match = re.fullmatch(r"@ref/(0x[0-9a-f]+)", reference)
    if not match:
        stop(f"{context} is not a compiled resource reference")
    resource_id = match.group(1)
    full_name = f"{PACKAGE}:xml/{expected_name}"
    if resources.get(resource_id) != full_name:
        stop(f"{context} does not resolve to {full_name}")
    matching_ids = [key for key, value in resources.items() if value == full_name]
    if matching_ids != [resource_id]:
        stop(f"{full_name} is not bound to exactly one compiled resource id")
    # There is no safe version/night/locale alternate for these three policy
    # resources.  Verify the actual manifest-bound id has one concrete value,
    # and that it is the default.  This also rejects a spec-only fabricated
    # table rather than silently inspecting a literal unrelated XML path.
    expected_path = f"res/xml/{expected_name}.xml"
    if configurations.get(resource_id) != [("default", expected_path)]:
        stop(f"{full_name} must have exactly one default compiled resource value")
    return resource_id


def normalize_aapt_dump(path: Path) -> str:
    value = path.read_text(encoding="utf-8")
    return re.sub(r" \(line=[0-9]+\)", "", value)


def semantic_xml(element: ET.Element) -> list[object]:
    return [
        element.tag,
        sorted(element.attrib.items()),
        (element.text or "").strip(),
        [semantic_xml(child) for child in element],
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("resource_table", type=Path)
    parser.add_argument("network_policy", type=Path)
    parser.add_argument("extraction_policy", type=Path)
    parser.add_argument("file_paths_policy", type=Path)
    parser.add_argument("package")
    parser.add_argument("version_name")
    parser.add_argument("version_code")
    parser.add_argument("commit")
    parser.add_argument("backend")
    parser.add_argument("privacy_status")
    parser.add_argument("privacy_notice_version")
    arguments = parser.parse_args()
    raw = arguments.manifest.read_bytes()
    root = ET.fromstring(raw)
    if root.tag != "manifest" or root.get("package") != PACKAGE or arguments.package != PACKAGE:
        stop("application id changed")
    expected_root_attributes = {
        ANDROID + "versionCode": arguments.version_code,
        ANDROID + "versionName": arguments.version_name,
        ANDROID + "compileSdkVersion": "36",
        ANDROID + "compileSdkVersionCodename": "16",
        "package": PACKAGE,
        "platformBuildVersionCode": "36",
        "platformBuildVersionName": "16",
    }
    if root.attrib != expected_root_attributes:
        stop("root manifest identity/SDK attribute allowlist changed")
    sdk = root.findall("uses-sdk")
    if len(sdk) != 1 or attribute(sdk[0], "minSdkVersion") != "26" or attribute(
        sdk[0], "targetSdkVersion"
    ) != "36":
        stop("minSdk/targetSdk must be exactly 26/36")
    verify_attributes(
        sdk[0], {"minSdkVersion": "26", "targetSdkVersion": "36"}, "uses-sdk"
    )
    allowed_root_children = {"uses-sdk", "uses-permission", "permission", "uses-feature", "application"}
    if any(child.tag not in allowed_root_children for child in root):
        stop("unexpected top-level manifest declaration")
    permissions = [attribute(item, "name") for item in root.findall("uses-permission")]
    if len(permissions) != len(set(permissions)) or set(permissions) != PERMISSIONS:
        stop("permission allowlist changed")
    if any(set(item.attrib) != {ANDROID + "name"} for item in root.findall("uses-permission")):
        stop("permission carries an unexpected qualifier")
    declarations = root.findall("permission")
    if len(declarations) != 1 or attribute(
        declarations[0], "name"
    ) != f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" or attribute(
        declarations[0], "protectionLevel"
    ) != "0x2":
        stop("dynamic-receiver signature permission changed")
    verify_attributes(
        declarations[0],
        {
            "name": f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            "protectionLevel": "0x2",
        },
        "signature permission",
    )
    features = {
        (attribute(item, "name"), attribute(item, "glEsVersion"), attribute(item, "required"))
        for item in root.findall("uses-feature")
    }
    if features != {("", "0x30000", "true"), ("android.hardware.wifi", "", "false")}:
        stop("hardware feature contract changed")
    feature_items = root.findall("uses-feature")
    if len(feature_items) != 2:
        stop("hardware feature uniqueness changed")
    for item in feature_items:
        if attribute(item, "name"):
            verify_attributes(
                item,
                {"name": "android.hardware.wifi", "required": "false"},
                "Wi-Fi feature",
            )
        else:
            verify_attributes(
                item,
                {"glEsVersion": "0x30000", "required": "true"},
                "OpenGL feature",
            )
    applications = root.findall("application")
    if len(applications) != 1:
        stop("manifest must contain exactly one application")
    application = applications[0]
    verify_attributes(
        application,
        APPLICATION_ATTRIBUTES,
        "application",
        optional={"debuggable": "false"},
    )
    resources, resource_configurations = resource_table(arguments.resource_table)
    network_resource_id = require_xml_resource(
        attribute(application, "networkSecurityConfig"),
        "network_security_config",
        resources,
        resource_configurations,
        "application networkSecurityConfig",
    )
    extraction_resource_id = require_xml_resource(
        attribute(application, "dataExtractionRules"),
        "data_extraction_rules",
        resources,
        resource_configurations,
        "application dataExtractionRules",
    )
    allowed_application_children = {"activity", "activity-alias", "service", "receiver", "provider", "meta-data"}
    if any(child.tag not in allowed_application_children for child in application):
        stop("unexpected application manifest declaration")
    metadata_items = application.findall("meta-data")
    metadata_names = [attribute(item, "name") for item in metadata_items]
    metadata = {attribute(item, "name"): attribute(item, "value") for item in metadata_items}
    required_metadata = {
        "com.dailybeat.app.patrolgrid.RELEASE_COMMIT": arguments.commit,
        "com.dailybeat.app.patrolgrid.BACKEND_IDENTITY": arguments.backend,
        "com.dailybeat.app.patrolgrid.PRIVACY_POLICY_STATUS": arguments.privacy_status,
        "com.dailybeat.app.patrolgrid.PRIVACY_NOTICE_VERSION": arguments.privacy_notice_version,
    }
    if any(metadata.get(name) != value for name, value in required_metadata.items()):
        stop("commit/backend/privacy metadata changed")
    expected_metadata = set(required_metadata) | {"com.google.android.gms.version"}
    if (len(metadata_items) != len(expected_metadata)
            or len(metadata_names) != len(set(metadata_names))
            or set(metadata) != expected_metadata):
        stop("application metadata allowlist or uniqueness changed")
    for item in metadata_items:
        verify_attributes(
            item,
            {"name": attribute(item, "name"), "value": attribute(item, "value")},
            f"application metadata {attribute(item, 'name')}",
        )
    if not re.fullmatch(r"@ref/0x[0-9a-f]+", metadata["com.google.android.gms.version"]):
        stop("Google Play services metadata reference changed")
    if not re.fullmatch(r"[0-9a-f]{40}", arguments.commit):
        stop("commit is not a full lowercase Git SHA")
    parsed = urllib.parse.urlsplit(arguments.backend)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment or parsed.path not in ("", "/")):
        stop("backend is not an exact HTTPS origin")
    if arguments.privacy_status != "APPROVED" or arguments.privacy_notice_version != "3":
        stop("privacy policy is not approved notice version 3")
    exported = set()
    for component_type in ("activity", "activity-alias", "service", "receiver", "provider"):
        components = application.findall(component_type)
        names = [attribute(component, "name") for component in components]
        if len(names) != len(set(names)) or set(names) != COMPONENTS[component_type]:
            stop(f"{component_type} component allowlist or uniqueness changed")
        for component in components:
            component_name = attribute(component, "name")
            verify_attributes(
                component,
                COMPONENT_ATTRIBUTES[component_name],
                f"{component_type} {component_name}",
            )
            if any(child.tag not in {"intent-filter", "meta-data"} for child in component):
                stop("component contains an unexpected declaration")
            exported_value = attribute(component, "exported")
            if exported_value not in ("true", "false"):
                stop(f"component lacks explicit exported state: {attribute(component, 'name')}")
            if component_type == "provider" and exported_value != "false":
                stop("an exported provider is forbidden")
            if exported_value == "true":
                exported.add((component_type, attribute(component, "name"), attribute(component, "permission")))
            filters = []
            for intent_filter in component.findall("intent-filter"):
                if intent_filter.attrib:
                    stop("intent filter carries an unexpected attribute")
                if any(child.tag not in {"action", "category"} for child in intent_filter):
                    stop("intent filter contains data or another unexpected declaration")
                for child in intent_filter:
                    verify_attributes(
                        child,
                        {"name": attribute(child, "name")},
                        f"intent-filter {child.tag}",
                    )
                actions = tuple(attribute(item, "name") for item in intent_filter.findall("action"))
                categories = tuple(attribute(item, "name") for item in intent_filter.findall("category"))
                filters.append((actions, categories))
            if filters != INTENT_FILTERS.get(component_name, []):
                stop(f"intent-filter contract changed: {component_name}")
            if component_type != "provider" and component.findall("meta-data"):
                stop(f"non-provider component metadata is forbidden: {component_name}")
    if exported != EXPORTED_COMPONENTS:
        stop("exported component allowlist changed")
    providers = {attribute(item, "name"): item for item in application.findall("provider")}
    provider_metadata = {
        "androidx.startup.InitializationProvider": {
            "androidx.work.WorkManagerInitializer": ("androidx.startup", ""),
            "androidx.emoji2.text.EmojiCompatInitializer": ("androidx.startup", ""),
            "androidx.lifecycle.ProcessLifecycleInitializer": ("androidx.startup", ""),
            "androidx.profileinstaller.ProfileInstallerInitializer": ("androidx.startup", ""),
        },
    }
    file_provider_items = providers["androidx.core.content.FileProvider"].findall("meta-data")
    if len(file_provider_items) != 1:
        stop("FileProvider metadata allowlist changed")
    file_provider_item = file_provider_items[0]
    verify_attributes(
        file_provider_item,
        {
            "name": "android.support.FILE_PROVIDER_PATHS",
            "resource": attribute(file_provider_item, "resource"),
        },
        "FileProvider paths metadata",
    )
    file_paths_resource_id = require_xml_resource(
        attribute(file_provider_item, "resource"),
        "file_paths",
        resources,
        resource_configurations,
        "FileProvider paths metadata",
    )
    for provider_name, expected in provider_metadata.items():
        items = providers[provider_name].findall("meta-data")
        observed = {}
        for item in items:
            verify_attributes(
                item,
                {"name": attribute(item, "name"), "value": attribute(item, "value")},
                f"provider metadata {attribute(item, 'name')}",
            )
            observed[attribute(item, "name")] = (attribute(item, "value"), "")
        if len(items) != len(expected) or len(observed) != len(expected) or observed != expected:
            stop(f"provider metadata contract changed: {provider_name}")
    if normalize_aapt_dump(arguments.network_policy) != NETWORK_POLICY:
        stop("network security config changed")
    if normalize_aapt_dump(arguments.extraction_policy) != EXTRACTION_POLICY:
        stop("backup/data-extraction policy changed")
    if normalize_aapt_dump(arguments.file_paths_policy) != FILE_PATHS_POLICY:
        stop("FileProvider paths policy changed")
    digest_input = json.dumps(
        {
            "manifest": semantic_xml(root),
            "networkPolicy": NETWORK_POLICY,
            "extractionPolicy": EXTRACTION_POLICY,
            "filePathsPolicy": FILE_PATHS_POLICY,
            "resourceBindings": {
                network_resource_id: f"{PACKAGE}:xml/network_security_config",
                extraction_resource_id: f"{PACKAGE}:xml/data_extraction_rules",
                file_paths_resource_id: f"{PACKAGE}:xml/file_paths",
            },
        },
        ensure_ascii=True,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    sys.stdout.write(hashlib.sha256(digest_input).hexdigest() + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
