import hashlib
import importlib.util
from pathlib import Path

import pytest


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


recovery = load('local_database_recovery_drill')
schema = load('verify_schema_fingerprints')


@pytest.mark.parametrize('origin,container', [
    ('https://mrhffxtuzxqzcqicchoj.supabase.co', 'supabase_db_dailybeat-hardening-123'),
    ('http://127.0.0.1:54361', 'supabase_db_unrelated'),
    ('http://localhost.evil.invalid', 'supabase_db_dailybeat-hardening-123'),
    ('http://user:secret@localhost', 'supabase_db_dailybeat-hardening-123'),
    ('http://localhost/remote', 'supabase_db_dailybeat-hardening-123'),
    ('http://localhost?remote=1', 'supabase_db_dailybeat-hardening-123'),
])
def test_restore_rejects_remote_or_unrelated_targets(origin, container):
    with pytest.raises(recovery.DrillError):
        recovery.validate_target(origin, container)


def test_restore_accepts_only_named_local_stack():
    recovery.validate_target('http://127.0.0.1:54361', 'supabase_db_dailybeat-hardening-123')


def test_schema_guard_rejects_changed_missing_extra_and_duplicate_components(tmp_path):
    source = tmp_path / '001.sql'
    source.write_text('select 1;')
    baseline = {'components': {'table:backup': 'abc'},
                'migration_sha256': {source.name: hashlib.sha256(source.read_bytes()).hexdigest()}}
    schema.verify('table:backup|abc\n', baseline, tmp_path)
    for actual in ('table:backup|different', '', 'table:backup|abc\nfunction:extra|def',
                   'table:backup|abc\ntable:backup|abc'):
        with pytest.raises(ValueError):
            schema.verify(actual, baseline, tmp_path)
    source.write_text('select 2;')
    with pytest.raises(ValueError, match='Migrations changed'):
        schema.verify('table:backup|abc\n', baseline, tmp_path)
