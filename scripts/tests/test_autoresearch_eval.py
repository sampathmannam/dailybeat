"""The research runner must not conceal failures or imply a missing run succeeded."""
import importlib.util
from pathlib import Path
import pytest


spec = importlib.util.spec_from_file_location(
    "autoresearch_eval", Path(__file__).resolve().parents[1] / "autoresearch_eval.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def test_no_reports_yields_zero_tests():
    assert runner.summarize([]) == dict(tests=0, failures=0, errors=0, skipped=0, failed_cases=[])


def test_failures_errors_and_skips_are_preserved(tmp_path):
    report = tmp_path / "TEST-research.xml"
    report.write_text('''<testsuite tests="4" failures="1" errors="1" skipped="1">
      <testcase classname="Route" name="pass"/>
      <testcase classname="Route" name="broken"><failure message="bad"/></testcase>
      <testcase classname="Route" name="crash"><error message="bad"/></testcase>
      <testcase classname="Route" name="skip"><skipped/></testcase>
    </testsuite>''')
    assert runner.summarize([report]) == dict(
        tests=4, failures=1, errors=1, skipped=1, failed_cases=["Route.broken", "Route.crash"])


def test_all_suites_are_counted(tmp_path):
    reports = [tmp_path / "TEST-first.xml", tmp_path / "TEST-second.xml"]
    for report in reports:
        report.write_text('<testsuite tests="3" failures="0" errors="0" skipped="0"/>')
    assert runner.summarize(reports)["tests"] == 6


def test_malicious_junit_dtd_is_rejected(tmp_path):
    report = tmp_path / "TEST-malicious.xml"
    report.write_text('''<!DOCTYPE testsuite [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
    <testsuite tests="1"><testcase name="&secret;"/></testsuite>''')
    with pytest.raises(ValueError, match="DTD is forbidden"):
        runner.summarize([report])
