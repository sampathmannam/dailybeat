# DailyBeat v4.0.2 release report

## Scope

DailyBeat v4.0.2 completes the end-to-end hardening and polish pass. User-entered names, notes,
diaries, place labels, and cloud fields now share Unicode-safe bounds at both the screen and
repository layers. Place coordinates and recognition radii are validated before storage.

Async actions across Today, Days, Diary, and Settings prevent repeat submission, expose calm busy
states, announce results to assistive technology, and keep errors beside their recovery path. The
ordinary saved-place UI no longer displays precise coordinates and instead explains its useful
recognition distance.

## Version contract

- Android `versionCode`: `23`
- Android `versionName`: `4.0.2`
- Release marker: `4.0.2`
- Release request: `release/requests/v4.0.2.txt`
- Expected tag: `v4.0.2`
- Expected APK: `DailyBeat-v4.0.2.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
