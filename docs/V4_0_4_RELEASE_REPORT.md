# DailyBeat v4.0.4 release report

## Scope

DailyBeat v4.0.4 separates identity yellow from button yellow. The original high-visibility signal
yellow `#FFD60A` is restored to Material secondary roles, route and stop rendering, onboarding
accents, selected states, and launcher details. The calmer marigold `#EAAA00` remains intentionally
limited to yellow-toned button surfaces, including shared secondary actions and route replay.

The neutral Carbon dark system introduced in v4.0.3 remains unchanged: canvas `#090A0C`, surface
`#121418`, and elevated surface `#1A1D23`. Signal yellow with ink measures above WCAG AA, as does
marigold button text; disabled behavior continues to come from Material 3 button state colors.

## Version contract

- Android `versionCode`: `25`
- Android `versionName`: `4.0.4`
- Release marker: `4.0.4`
- Release request: `release/requests/v4.0.4.txt`
- Expected tag: `v4.0.4`
- Expected APK: `DailyBeat-v4.0.4.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
