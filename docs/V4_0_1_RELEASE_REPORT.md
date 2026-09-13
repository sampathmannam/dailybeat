# DailyBeat v4.0.1 release report

## Scope

DailyBeat v4.0.1 ships the simplified Days-card hierarchy and the completed hardening pass. The
Days list is now a scan-friendly journey index: generated diary prose and the retired midday-pulse
summary no longer appear inside each card, while the underlying diary remains unchanged and
available from the day view.

The release also retains the security controls added after v4.0.0: secret scanning, strict Gradle
dependency verification, explicit Android component exposure, restricted backup/network policy,
owner-only cloud backup rules, and a publisher that waits for the complete protected-commit gate.

## Version contract

- Android `versionCode`: `22`
- Android `versionName`: `4.0.1`
- Release marker: `4.0.1`
- Release request: `release/requests/v4.0.1.txt`
- Expected tag: `v4.0.1`
- Expected APK: `DailyBeat-v4.0.1.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
