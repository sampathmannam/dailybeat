# DailyBeat v4.0.0 release report

Date: 2026-09-12

## Outcome

DailyBeat v4.0.0 is a phone-only release candidate aligned with the approved Penpot v4 architecture. The width-triggered tablet navigation rail has been removed; Today, Days, Insights, and Settings retain bottom navigation in portrait and landscape. The previously completed journey-ledger improvements remain intact: visible local moments, a reassuring privacy-pause state, capture-gap disclosure, GPS-fix freshness, and privacy-first Settings order.

No database schema, dependency, permission, cloud payload, signing configuration, or production data path changed.

## Version

- `versionCode 21`
- `versionName 4.0.0`
- Release marker: `4.0.0`
- Release request: `release/requests/v4.0.0.txt`

## Verification

- Python policy tests: 38 passed
- Android debug build: passed
- Android JVM tests: 233 passed
- Android lint: passed with 0 errors
- Debug and instrumentation APK assembly: passed
- API 34 connected instrumentation: 33 finished, 0 failures, 2 environment skips

The two skips are the existing live-Supabase test without credentials and fused-location re-arm test on an AOSP image without Google Play Services. Protected CI retains the mandatory independent live-backup gate.

An initial connected run was invalidated by a concurrent DSR instrumentation job using the same emulator and repeatedly taking foreground focus. The isolated `aosp_default_34` rerun completed with zero failures.

## QA artifact

- Package: `com.dailybeat.app.qa.e2eloop`
- APK: `outputs/dailybeat-v4.0.0-qa/DailyBeat-v4.0.0-QA-debug.apk`
- SHA-256: `b09c1d9d6322e8561a0e51858daa2f9d6ad725b8b996f003626252c4d09a18dc`
- Instrumentation APK SHA-256: `5ad4ad53b2ee8b1f1045a2d6822dae59baa7c47b8c06f55e8b1f6dc488d125c7`

The QA package is isolated from production and uses the Android debug signing identity. The signed production APK must be built only by the protected GitHub release workflow with the permanent signing key.

