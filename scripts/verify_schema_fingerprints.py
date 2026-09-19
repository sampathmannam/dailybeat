#!/usr/bin/env python3
"""Compare metadata-only SQL results against the reviewed migration baseline."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys


def verify(lines: str, baseline: dict, migrations: Path) -> None:
    observed = {}
    for line in lines.splitlines():
        if not line.strip():
            continue
        fields = line.strip().split('|')
        if len(fields) != 2 or fields[0] in observed:
            raise ValueError('Malformed or duplicate schema component')
        observed[fields[0]] = fields[1]
    expected = baseline['components']
    changed = sorted(key for key in observed.keys() | expected.keys()
                     if observed.get(key) != expected.get(key))
    if changed:
        raise ValueError('Schema drift: ' + ', '.join(changed))
    actual_sources = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in migrations.glob('*.sql')}
    if actual_sources != baseline['migration_sha256']:
        raise ValueError('Migrations changed: review and regenerate the schema baseline')


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, default=root / 'supabase/schema-baseline.json')
    parser.add_argument('--migrations', type=Path, default=root / 'supabase/migrations')
    args = parser.parse_args()
    try:
        verify(sys.stdin.read(), json.loads(args.baseline.read_text()), args.migrations)
    except (ValueError, KeyError, OSError) as error:
        raise SystemExit(str(error)) from None
    print('All schema components and migration source hashes match the reviewed baseline.')


if __name__ == '__main__':
    main()
