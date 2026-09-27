#!/usr/bin/env python3
"""Run a bounded, local-only DailyBeat regression evaluation; never install or release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from xml.parsers import expat

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "android/app/src/test/java/com/dailybeat/app/ui/components/JourneyMapResearchTest.kt"


def summarize(reports):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    failed = []
    for report in reports:
        # Gradle's generated JUnit report is the only input. Bound its size and
        # reject DTDs/entities so a replaced report cannot expand or read files.
        if report.stat().st_size > 10_000_000:
            raise ValueError(f"Oversized JUnit report: {report}")
        parser = expat.ParserCreate()
        parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)

        def reject_doctype(*_):
            raise ValueError("JUnit DTD is forbidden")

        def reject_external_entity(*_):
            raise ValueError("External entity is forbidden")

        parser.StartDoctypeDeclHandler = reject_doctype
        parser.ExternalEntityRefHandler = reject_external_entity
        current_case = None

        def start(name, attrs):
            nonlocal current_case
            if name == "testsuite":
                for key in totals:
                    totals[key] += int(attrs.get(key, "0"))
            elif name == "testcase":
                current_case = [attrs.get("classname", ""), attrs.get("name", ""), False]
            elif name in ("failure", "error") and current_case is not None:
                current_case[2] = True

        def end(name):
            nonlocal current_case
            if name == "testcase" and current_case is not None:
                if current_case[2]:
                    failed.append(f"{current_case[0]}.{current_case[1]}")
                current_case = None

        parser.StartElementHandler = start
        parser.EndElementHandler = end
        parser.Parse(report.read_bytes(), True)
    return dict(totals, failed_cases=failed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label", help="Unique experiment label; an existing run cannot be overwritten")
    parser.add_argument("--full", action="store_true", help="Run all FOSS/store JVM tests")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}", args.label):
        parser.error("Use a short alphanumeric/hyphen/underscore label")

    destination = ROOT / ".autoresearch" / args.label
    destination.mkdir(parents=True, exist_ok=False)
    command = [
        "./gradlew", ":app:testDebugUnitTest", "-PdailybeatFoss=true",
        "-PdailybeatStore=true", "-PdailybeatUnsigned=true", "--console=plain", "--rerun-tasks",
    ]
    if not args.full:
        for name in ("JourneyMapResearchTest", "JourneyMapModelTest", "JourneyPlaybackStateTest"):
            command += ["--tests", f"com.dailybeat.app.ui.components.{name}"]

    def git(*arguments):
        return subprocess.check_output(["git", *arguments], cwd=ROOT)

    # Hash the entire tracked working diff too: HEAD alone cannot identify uncommitted candidates.
    result = {
        "label": args.label, "full": args.full, "base_commit": git("rev-parse", "HEAD").decode().strip(),
        "working_diff_sha256": hashlib.sha256(git("diff", "HEAD", "--binary")).hexdigest(),
        "fixture_sha256": hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),
        "evaluator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "command": command,
        "limitations": "Host JVM correctness only; not device map rendering, GPS accuracy or battery drain.",
    }
    started = time.time()
    with (destination / "gradle.log").open("w") as log:
        try:
            # A separate process group lets the deadline stop this invocation, not other Gradle work.
            process = subprocess.Popen(command, cwd=ROOT / "android", stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                returncode = process.wait(timeout=25 * 60)
            except subprocess.TimeoutExpired:
                import signal
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                returncode = 124
        except OSError as error:
            log.write(f"Could not start evaluation: {error}\n")
            returncode = 127

    reports = sorted((ROOT / "android/app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    # Never score reports left behind by an earlier successful invocation.
    fresh = [path for path in reports if path.stat().st_mtime >= started]
    result.update(summarize(fresh))
    result.update(returncode=returncode, elapsed_seconds=round(time.time() - started, 2))
    result["passed"] = (returncode == 0 and result["tests"] > 0 and
                        result["failures"] == result["errors"] == result["skipped"] == 0)
    for path in fresh:
        (destination / path.name).write_bytes(path.read_bytes())
    (destination / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    print(f"Evidence: {destination}")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
