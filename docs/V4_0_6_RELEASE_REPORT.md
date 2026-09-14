# DailyBeat v4.0.6 release report

## Scope

DailyBeat v4.0.6 applies the selected Soft Sun yellow `#F0C94A` to every yellow role: shared
buttons, route and stop rendering, launcher details, selected accents, and onboarding. The more
restrained hue preserves the daily-ledger identity without the fluorescent intensity of the prior
signal yellow. Carbon dark mode remains neutral: canvas `#090A0C`, surface `#121418`, and elevated
surface `#1A1D23`.

Soft Sun with ink measures above WCAG AA for button text. The pale light-theme and deep dark-theme
containers were retuned to the new hue while Material 3 continues to provide disabled states.

## Version contract

- Android `versionCode`: `27`
- Android `versionName`: `4.0.6`
- Release marker: `4.0.6`
- Release request: `release/requests/v4.0.6.txt`
- Expected tag: `v4.0.6`
- Expected APK: `DailyBeat-v4.0.6.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
