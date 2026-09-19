# DailyBeat v4.1.3 release report

Date: 19 September 2026

## Outcome

DailyBeat v4.1.3 is a focused journey-card, colour, capture-status, and release-integrity update for
the existing `com.dailybeat.app` installation. It advances Android version code 30 to 31, allowing
Obtainium and Android to install it over v4.1.2 without uninstalling the app or clearing local
journal data.

## User-visible changes

- Today restores its full capture status and summary without a **More** control.
- Each Days card keeps only its map, Distance, Time out, and Auto stops visible by default. The
  card's own **More** / **Less** control reveals or hides the ordered “Where you went” place history.
- Warm Butter `#EED77B` replaces the overly bright yellow across primary yellow actions, map routes,
  stop markers, selected accents, and launcher details. Neutral Carbon dark-mode surfaces remain
  unchanged.
- Intentional adaptive standby now says Android is watching for movement and will resume route
  capture when movement is detected; it no longer looks like capture was switched off.

## Reliability and security changes

- Published release APKs and checksums are immutable. The publisher refuses an existing release
  instead of replacing assets that an Obtainium client may already have downloaded.
- The Android instrumentation wrapper stops immediately if its expected checkout directory is
  unavailable.
- A project-specific OpenCodeReview policy covers capture, standard/FOSS parity, encrypted backup,
  Room integrity, outbound privacy, Compose accessibility, Supabase RLS, releases, and device scripts.

## Release identity

- Android package: `com.dailybeat.app`
- Android `versionCode`: `31`
- Android `versionName`: `4.1.3`
- Release marker and tag: `4.1.3` / `v4.1.3`
- APK asset: `DailyBeat-v4.1.3.apk`
- Integrity asset: `SHA256SUMS.txt`
- Permanent signing certificate SHA-256:
  `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`

## Verification contract

The release commit must pass standard and Google-free Android builds, JVM and repository-policy
tests, lint, Google-free dependency verification, both Android 14 instrumentation lanes, live and
native encrypted-backup recovery, executable Supabase RLS isolation, release policy, dependency
review, CodeQL, and open-source secret/configuration/dependency scanning. The publisher waits for
those checks on the exact protected `main` commit before generating the signed APK and checksums.

The protected feature commits passed the complete build, FOSS, security, backup, database-policy,
and online/offline instrumentation matrix. Local colour validation also covered the standard and
Google-free builds, focused Today/Days emulator flows, and dark-mode visual review.

## Honest limitations

- GPS remains an observation aid, not proof of activity or an evidentiary chain of custody.
- Battery performance and stop recall still require the documented physical-device field protocol;
  emulator and host checks cannot establish real-world drain on every phone.
- OpenCodeReview provides deterministic policy-guided coverage but is not a formal security audit.
- Warm Butter improves visual calm and retains strong dark-ink contrast; device-specific display
  calibration and accessibility preferences may still change its appearance.
