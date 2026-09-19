# DailyBeat v4.1.2 release report

Date: 19 September 2026

## Outcome

DailyBeat v4.1.2 is a focused interaction and notification update for the existing
`com.dailybeat.app` installation. It advances Android version code 29 to 30, allowing Obtainium and
Android to install it over v4.1.1 without uninstalling the app or clearing local journal data.

## User-visible changes

- Insights now uses the current calendar week from Monday through Sunday instead of a rolling
  seven-day interval. The chart keeps all seven calendar positions and uses unambiguous day labels.
- Today retains the map and compact Distance / Time / Auto stops summary. Place names begin
  collapsed and only the ordered “Where you went” list opens from the compact **More** control.
- Passive capture still uses Android's required foreground-service notification. Its dedicated,
  low-importance status channel is silent and does not create an unread launcher-icon badge.
- Licences & resources now separates the DailyBeat GPL summary, source repository, third-party
  notices, GPL text, and Apache text into clear expandable sections that remain available offline.
- DailyBeat's original Signal Yellow `#FFD60A` is restored for identity accents, routes, stops,
  launcher details, and primary yellow actions; Carbon dark-mode surfaces remain neutral.

## Release identity

- Android package: `com.dailybeat.app`
- Android `versionCode`: `30`
- Android `versionName`: `4.1.2`
- Release marker and tag: `4.1.2` / `v4.1.2`
- APK asset: `DailyBeat-v4.1.2.apk`
- Integrity asset: `SHA256SUMS.txt`
- Permanent signing certificate SHA-256:
  `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`

## Verification contract

The release commit must pass the standard and Google-free Android builds, JVM and repository policy
tests, lint, Google-free dependency verification, both Android 14 instrumentation lanes, live and
native encrypted-backup recovery, executable Supabase RLS isolation, release policy, dependency
review, CodeQL, and open-source secret/configuration/dependency scanning. The publisher waits for
those checks on the exact protected `main` commit before generating the signed APK and checksums.

The pre-release feature commit passed 291 JVM tests and 67 repository policy tests. A disposable
Android 14 emulator passed the focused notification/Today/legal suites locally, and the protected
remote matrix passed both complete 39-test instrumentation lanes before the release version change.

## Honest limitations

- A real evening reminder is still allowed to create a launcher badge; only continuous passive
  capture is treated as status rather than unread content.
- The unused legacy capture notification channel may remain listed in Android system settings after
  an update. DailyBeat posts new capture status only to the no-badge v2 channel.
- GPS remains an observation aid, not proof of activity or an evidentiary chain of custody.
- Battery performance and stop recall still require the planned physical-device field protocol;
  emulator and host checks cannot establish real-world drain on every device.
