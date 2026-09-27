from scripts.fdroid_source_check import scanner_reported_problem


def test_fdroid_source_scanner_fails_on_reported_findings_even_with_zero_exit():
    assert scanner_reported_problem("WARNING: Scanner found 1 problems in com.dailybeat.app:42")
    assert scanner_reported_problem("1 problems found\n")
    assert not scanner_reported_problem("INFO: Finished\n")
    assert not scanner_reported_problem("0 problems found\n")
