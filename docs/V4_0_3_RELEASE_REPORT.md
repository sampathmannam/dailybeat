# DailyBeat v4.0.3 release report

## Scope

DailyBeat v4.0.3 refines the complete Android color system. Marigold `#EAAA00` replaces the former
fluorescent signal yellow across Material roles, route and stop rendering, launch assets, and
identity details. Dark mode uses neutral Carbon canvas `#090A0C`, surface `#121418`, and elevated
surface `#1A1D23` instead of navy-tinted layers.

The palette keeps meaning restrained: navy remains the light-theme primary structure, marigold
marks identity and selected states, and warning/error/success colors retain their semantic roles.
Dark text and boundary pairs meet or exceed the relevant WCAG AA contrast thresholds. The verified
ratios include 18.16:1 for primary text on the Carbon canvas, 10.59:1 for muted text on the canvas,
4.82:1 for outlines on the canvas, and 10.37:1 for marigold-container text.

## Version contract

- Android `versionCode`: `24`
- Android `versionName`: `4.0.3`
- Release marker: `4.0.3`
- Release request: `release/requests/v4.0.3.txt`
- Expected tag: `v4.0.3`
- Expected APK: `DailyBeat-v4.0.3.apk`

## Required release evidence

The publisher must observe successful `build`, `offline-instrumentation`, `instrumentation`,
`live-backup`, `release-policy`, `codeql`, and `oss-security` checks on the exact protected `main`
commit. It then builds with the permanent release key, verifies certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and publishes the APK with
`SHA256SUMS.txt`.
