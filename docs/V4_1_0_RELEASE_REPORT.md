# DailyBeat v4.1.0 release report

Date: 18 September 2026

## Outcome

DailyBeat v4.1.0 is the production update for the existing `com.dailybeat.app` installation. It is
published only from the protected `main` commit after the complete automated release matrix passes,
then signed with the same permanent certificate as prior releases. Its version code advances from
27 to 28, so Obtainium and Android can install it in place without deleting local data.

## User-visible changes

- Today opens with the route map and one compact Distance / Time / Auto stops summary. Place names,
  stop times, coverage and capture details are disclosed with **More**.
- Tracked time sums continuous measured capture intervals and excludes long gaps instead of showing
  the first-to-last wall-clock span that could misleadingly read 21–23 hours.
- Passive capture adapts to movement and confirmed stillness to reduce unnecessary location work.
- Personal, Field work and Police templates share the same privacy-first local journal.
- Client-encrypted cloud recovery uses a separate passphrase that never leaves the phone.
- Retention, correction history, diary checkpoints, cloud deletion, account deletion and complete
  local erasure provide explicit lifecycle control.
- GPL-3.0-only licensing and bundled third-party notices prepare the project for public review.

## Release identity

- Android package: `com.dailybeat.app`
- Android `versionCode`: `28`
- Android `versionName`: `4.1.0`
- Release marker and tag: `4.1.0` / `v4.1.0`
- APK asset: `DailyBeat-v4.1.0.apk`
- Integrity asset: `SHA256SUMS.txt`
- Permanent signing certificate SHA-256:
  `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`

## Publication contract

The publisher waits for standard and Google-free builds, online and offline Android instrumentation,
native and transport backup recovery, release policy, executable RLS isolation, CodeQL, dependency
review and open-source security checks on the exact protected commit. It then verifies the permanent
certificate fingerprint before creating the immutable tag and release assets.

The 14-day battery/stop-recall protocol remains a field-measurement program, not a claimed battery
drain guarantee. F-Droid or another third-party store still requires that store's own dependency,
asset and signing review; this GitHub release is the supported Obtainium update channel.
