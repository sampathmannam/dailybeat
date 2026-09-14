# DailyBeat v4.0.5 release report

## Scope

DailyBeat v4.0.5 uses the original high-visibility signal yellow `#FFD60A` for every yellow
surface and accent, including shared secondary buttons and the route replay action. The temporary
marigold `#EAAA00` button override is removed entirely. The neutral Carbon dark system remains
unchanged: canvas `#090A0C`, surface `#121418`, and elevated surface `#1A1D23`.

Signal yellow with ink measures above WCAG AA in both themes. Disabled button behavior continues
to come from Material 3 state colors.

## Version contract

- Android `versionCode`: `26`
- Android `versionName`: `4.0.5`
- Release marker: `4.0.5`
- Release request: `release/requests/v4.0.5.txt`
- Expected tag: `v4.0.5`
- Expected APK: `DailyBeat-v4.0.5.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
