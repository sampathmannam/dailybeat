#!/usr/bin/env python3
"""Record repeatable cold-launch samples from a disposable QA install. Never clears app data."""
import argparse, json, re, statistics, subprocess, time
from pathlib import Path


def measure(serial, package, runs):
    if package != "com.dailybeat.app.qa.e2eloop":
        raise ValueError("Benchmarks may control only the isolated .qa.e2eloop package")
    def adb(*args):
        return subprocess.check_output(["adb", "-s", serial, *args], text=True, timeout=60)
    installed = adb("shell", "pm", "path", package)
    if "package:" not in installed:
        raise RuntimeError("Install the QA APK first")
    samples = []
    for _ in range(runs):
        adb("shell", "am", "force-stop", package)
        result = adb("shell", "am", "start", "-W", "-n", package + "/com.dailybeat.app.MainActivity")
        match = re.search(r"TotalTime:\s*(\d+)", result)
        if not match or "Status: ok" not in result:
            raise RuntimeError("Activity did not start successfully")
        samples.append(int(match[1]))
        time.sleep(2)
    return {"package": package, "runs": runs, "coldStartMs": samples,
            "medianMs": statistics.median(samples), "maxMs": max(samples),
            "androidApi": adb("shell", "getprop", "ro.build.version.sdk").strip(),
            "isEmulator": adb("shell", "getprop", "ro.kernel.qemu").strip() == "1",
            "interpretation": "Local launch timing only; compare the same device, build type, dataset and thermal state."}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial"); parser.add_argument("output", type=Path)
    parser.add_argument("--runs", type=int, default=5)
    args = parser.parse_args()
    if not 3 <= args.runs <= 20: parser.error("Use 3–20 runs")
    result = measure(args.serial, "com.dailybeat.app.qa.e2eloop", args.runs)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Recorded {args.runs} launches; median {result['medianMs']} ms")
