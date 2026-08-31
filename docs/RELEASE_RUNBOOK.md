# DailyBeat Release and Rollback Runbook

This runbook covers the v3.5.0 release candidate. The pre-release last-known-good
version is **v3.5.0**. Do not promote a newer build until every applicable gate
below passes and its evidence contains no keys, prompts, model output, personal
data, or production-package state.

## Local deterministic load gate

The target is only the repository's synthetic localhost mock. Never point Locust
at DeepSeek or another external service.

Terminal 1:

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/ -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
python tools/locust/mock_deepseek.py --port 8765
```

Terminal 2:

```powershell
locust -f tools/locust/locustfile.py --headless -u 20 -r 5 -t 60s --host http://127.0.0.1:8765 --only-summary
```

The gate requires at least 100 requests, zero unexpected failures, p95 <= 250 ms,
and p99 <= 500 ms. HTTP 429 and 500 responses count as successful contract
exercises only when their status and JSON error shape match the selected
synthetic scenario. Stop the mock when Locust exits.

## Motorola QA release gate

Use only `ZD2232FCR5`, `com.dailybeat.app.qa`, and
`com.dailybeat.app.qa.test`. Confirm production-package presence before and
after with `adb -s ZD2232FCR5 shell pm path com.dailybeat.app`, but do not
launch, clear, replace, or otherwise inspect production.

Do not use `pm clear` when the encrypted QA API key must be preserved.
`Task7ReleaseGateTest` is the controlled equivalent: before each loop it clears
the QA Room database and ordinary `dailybeat_settings`, explicitly disables
automatic cloud work, and does not access or modify encrypted key material.

Install the QA test APK, then run the loop gate three times with loop identifiers
1, 2, and 3:

```powershell
cd android
$env:ANDROID_SERIAL='ZD2232FCR5'
.\gradlew.bat --no-daemon :app:installDebugAndroidTest
1..3 | ForEach-Object {
    adb -s ZD2232FCR5 shell am instrument -w -r `
        -e task7Loop $_ `
        -e class com.dailybeat.app.Task7ReleaseGateTest `
        com.dailybeat.app.qa.test/androidx.test.runner.AndroidJUnitRunner
}
Remove-Item Env:ANDROID_SERIAL
```

Each loop must report `OK (1 test)`. The test completes onboarding, verifies
synthetic insertion counts 7/8 then 0/0, visits Today/Diary/History/Settings
without creating the full map, opens and closes three fully rendered dedicated
maps, rotates landscape/portrait, presses Home and resumes, and records PSS
before and after every map cycle. After each loop, require a live QA PID, zero
recent QA crash/ANR entries, and capture
`adb -s ZD2232FCR5 shell dumpsys meminfo com.dailybeat.app.qa`. Any strictly
increasing post-close PSS series fails the loop.

Only after all three loops pass, run exactly one capped live contract test:

```powershell
adb -s ZD2232FCR5 shell am instrument -w -r `
    -e class com.dailybeat.app.Task7LiveContractGateTest `
    com.dailybeat.app.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Do not rerun this command after it issues the provider request. The test makes
one direct daily-diary request capped at 900 output tokens, validates every
citation against the exact synthetic context, and persists only a valid report.
Retain only pass/fail, latency, output length, and citation IDs; never retain the
key, prompt, model text, or full output.

## Release artifact verification

Download `app-release.apk` and `SHA256SUMS.txt` from the same GitHub release.
Run these checks from the download directory:

```powershell
Get-FileHash .\app-release.apk -Algorithm SHA256
Get-Content .\SHA256SUMS.txt
apksigner verify --print-certs --verbose .\app-release.apk
```

Compare the complete SHA-256 value, not a prefix. Compare the certificate SHA-256
digest with the protected release-certificate fingerprint recorded by the
release owner. Stop if either value differs.

Install only after checksum and certificate verification:

```powershell
$env:ANDROID_SERIAL='<authorized-device-serial>'
adb install -r .\app-release.apk
adb shell pm path com.dailybeat.app
Remove-Item Env:ANDROID_SERIAL
```

For Task 7 device QA, use `:app:installDebug` and `com.dailybeat.app.qa` instead;
never run the production install commands during that QA pass.

## Promotion checklist

1. Confirm the release commit, version code/name, tag, and changelog agree.
2. Confirm unit, lint, instrumentation, mock-contract, and bounded-load gates pass.
3. Confirm three QA-package Motorola loops have no DailyBeat crash/ANR and no
   monotonic retained-memory growth across full-map cycles.
4. Verify the universal APK certificate and checksum with the commands above.
5. Attach only `app-release.apk` and `SHA256SUMS.txt` to the GitHub release.
6. Keep the release as a pre-release until manual install and launch verification
   completes, then promote it without replacing either artifact.

## Nondestructive GitHub release rollback

Do not delete a bad tag, GitHub release, artifact, or commit; preserving history
keeps the incident auditable and prevents a different binary from appearing
under an old version. Mark the affected GitHub release as a pre-release and add
a warning that names the symptom and the replacement version.

Use a forward rollback:

1. Identify the v3.5.0 last-known-good commit and verify its signed release
   artifacts again.
2. Revert the faulty commits on a new branch; do not reset or rewrite history.
3. Increment `versionCode` and `versionName`, create a new signed tag, and let the
   release workflow produce a new APK and checksum.
4. Run every release gate on that new artifact and publish it as the replacement.
5. Keep the affected release available but visibly marked as superseded. Update
   its notes to link to the replacement.

Android normally rejects installing an older version code over a newer one, and
uninstalling can destroy local data. A forward rollback avoids both hazards. If
an emergency downgrade is unavoidable, first export and verify supported user
data, obtain explicit owner approval, and document the data-loss risk.
