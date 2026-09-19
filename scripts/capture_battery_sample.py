#!/usr/bin/env python3
"""Read-only physical-phone measurements; never reset stats or modify an app."""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
import os
from pathlib import Path
import re
import subprocess
import sys

PACKAGES = {'com.dailybeat.app', 'com.dailybeat.app.qa', 'com.dailybeat.app.qa.e2eloop'}
FIELDS = ['timestamp_utc', 'device_serial', 'model', 'build_fingerprint', 'package',
          'version_name', 'phase', 'battery_level', 'status', 'plugged',
          'temperature_tenths_c', 'capture_backend', 'boot_id', 'phase_source', 'exclude_interval']


def battery_values(text: str) -> dict[str, str]:
    values = dict(re.findall(r'^[ \t]*([^:\n]+):[ \t]*([^\n]*)$', text, re.MULTILINE))
    level, scale, status = int(values['level']), int(values.get('scale', '100')), int(values['status'])
    if scale != 100 or not 0 <= level <= 100 or status not in range(1, 6):
        raise ValueError('Unsupported battery reading')
    powered = ['AC powered', 'USB powered', 'Wireless powered']
    if 'plugged' in values:
        plugged = int(values['plugged'])
        if plugged < 0:
            raise ValueError('Invalid power source')
    else:
        # Most Android dumps use powered booleans, not a `plugged:` field.
        if any(values.get(key, '').lower() not in {'true', 'false'} for key in powered):
            raise ValueError('Charging state is unknown')
        plugged = sum(bit for key, bit in zip(powered, [1, 2, 4]) if values[key].lower() == 'true')
        dock = values.get('Dock powered', 'false').lower()
        if dock not in {'true', 'false'}:
            raise ValueError('Dock power state is unknown')
        if dock == 'true':
            plugged |= 8
    temperature = int(values['temperature'])
    return {'battery_level': str(level), 'status': str(status), 'plugged': str(plugged),
            'temperature_tenths_c': str(temperature)}


def collect(serial: str, trial: str, phase: str, output: Path, package: str, backend: str) -> Path:
    if not re.fullmatch(r'[A-Za-z0-9._:-]+', serial) or not re.fullmatch(r'[A-Za-z0-9._-]+', trial):
        raise ValueError('Invalid device or trial identifier')
    if package not in PACKAGES or phase not in {'capture_on', 'capture_off'} or backend not in {'standard', 'platform', 'unknown'}:
        raise ValueError('Unsupported package, phase or backend')

    def adb(*args: str) -> str:
        return subprocess.check_output(['adb', '-s', serial, *args], text=True, timeout=30,
                                       stderr=subprocess.DEVNULL).replace('\r', '').strip()

    if adb('get-state') != 'device':
        raise ValueError('The physical phone is not available')
    if serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu') == '1':
        raise ValueError('Emulators cannot supply physical field-trial evidence')
    package_dump = adb('shell', 'dumpsys', 'package', package)
    version = re.search(r'\bversionName=([^\s]+)', package_dump)
    if not version:
        raise ValueError('DailyBeat is not installed on the selected phone')
    raw = adb('shell', 'dumpsys', 'battery')
    values = battery_values(raw)
    row = dict(timestamp_utc=datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z'),
               device_serial=serial, model=adb('shell', 'getprop', 'ro.product.model'),
               build_fingerprint=adb('shell', 'getprop', 'ro.build.fingerprint'), package=package,
               version_name=version.group(1), phase=phase, **values, capture_backend=backend,
               boot_id=adb('shell', 'cat', '/proc/sys/kernel/random/boot_id'),
               phase_source='operator', exclude_interval='no')
    if not re.fullmatch(r'[0-9a-fA-F-]{36}', row['boot_id']):
        raise ValueError('Cannot identify the current boot safely')
    samples = output / 'battery-samples.csv'
    if samples.exists():
        with samples.open(newline='', encoding='utf-8') as handle:
            if next(csv.reader(handle), None) != FIELDS:
                raise ValueError('Existing CSV uses another schema; start a new trial directory')
    os.umask(0o077)
    sample_dir = output / 'raw' / serial / row['timestamp_utc'].replace(':', '-')
    sample_dir.mkdir(parents=True, exist_ok=False)
    (sample_dir / 'battery.txt').write_text(raw + '\n')
    (sample_dir / 'package.txt').write_text(package_dump + '\n')
    (sample_dir / 'dailybeat-batterystats.txt').write_text(adb('shell', 'dumpsys', 'batterystats', '--charged', package) + '\n')
    (sample_dir / 'device-idle.txt').write_text(adb('shell', 'dumpsys', 'deviceidle') + '\n')
    exists = samples.exists()
    with samples.open('a', newline='', encoding='utf-8') as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        if not exists:
            writer.writeheader()
        writer.writerow(row)
    print('Read-only sample saved. Charging samples are excluded from discharge-rate comparisons.'
          if values['plugged'] != '0' or values['status'] != '3' else 'Read-only discharging sample saved.')
    return sample_dir


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('device_serial')
    parser.add_argument('trial_id')
    parser.add_argument('phase', choices=['capture_on', 'capture_off'])
    parser.add_argument('output_dir', nargs='?', type=Path)
    args = parser.parse_args()
    try:
        path = collect(args.device_serial, args.trial_id, args.phase,
                       args.output_dir or Path('deliverables') / ('battery-field-trial-' + args.trial_id),
                       os.environ.get('DAILYBEAT_PACKAGE', 'com.dailybeat.app'),
                       os.environ.get('DAILYBEAT_CAPTURE_BACKEND', 'unknown'))
        print(f'Output: {path}')
        return 0
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        # Do not echo subprocess output, package metadata, or user identifiers on failure.
        print('Field sample failed: ' + (str(error) if isinstance(error, (ValueError, KeyError)) else type(error).__name__), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
