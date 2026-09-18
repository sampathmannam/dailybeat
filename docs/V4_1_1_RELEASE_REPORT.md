# DailyBeat v4.1.1 release report

Date: 19 September 2026

## Outcome

DailyBeat v4.1.1 is a reliability and privacy update for the existing `com.dailybeat.app`
installation. It advances version code 28 to 29, so Obtainium and Android can install it over
v4.1.0 without uninstalling or deleting the local journal. Publication is allowed only from the
protected `main` commit after the complete release matrix passes; GitHub signs the release with the
same permanent Android certificate used by earlier versions.

## End-to-end feature audit

| Area | Normal user path checked | Reliability/privacy boundary |
|---|---|---|
| Onboarding and permissions | Personal, Field work and Police templates; optional name; location, activity, notification and microphone consent | Denial leaves notes and diaries usable; Settings explains recovery and links to Android settings |
| Passive capture | Start/stop, one-hour privacy pause, movement/stillness adaptation, update/boot re-arm, standard and Google-free backends | Location quality and impossible jumps are rejected; open visits survive service recreation; erase cancels pending writes |
| Today | Map, measured distance/time, auto-stop count, More details, moment and note capture, capture health | Midnight and time-zone changes rebind the live day; sparse fixes never claim 21–23 tracked hours |
| Days and search | Thirty-day card feed, route thumbnail, More stop details, local diary/note/place search | Empty/error states stay distinct; refreshes cancel stale loads; dense route rendering is bounded without changing metrics |
| Map | Immediate offline route, network tiles, full map, route replay, external OpenStreetMap handoff | Capture gaps remain broken; antimeridian and invalid points are handled; bitmap/tile memory is released on scroll/cancel |
| Review | Rename/hide/restore stops, title a Beat, complete/reopen | Optimistic concurrency rejects stale edits; every correction is append-only and backed up |
| Diary and voice | Autosave, clear/rewrite, local or optional cloud draft, revision restore, phone-language speech recognition | Unicode-safe limits; source-linked cloud validation; cloud failure keeps the local transcript and manual draft path |
| Insights | Weekly movement, streak, recurring places, next action | Reads local history only and handles no-data history without inventing conclusions |
| Sharing | Explicit preview, PDF, seven-day ZIP | Revalidates records/privacy before and after rendering; private zones/hidden stops are rebuilt from filtered sources; temporary files are cleared |
| Cloud backup | Sign-up/sign-in, token refresh, encrypted upload/download, explicit legacy recovery | AES-256-GCM envelope, bounded/validated snapshot, owner-only RLS, transactional Room restore, passphrase never uploaded or stored |
| Data lifecycle | 30/90/365-day retention, delete backups, delete account, erase this phone | Destructive confirmation, transactional pruning, capture stopped without finalizing erased visits, verified auxiliary-store cleanup |
| Appearance/accessibility | Phone portrait/landscape, System/Light/Carbon Dark, 200% type, TalkBack labels, 48 dp actions | Action progress blocks duplicate taps; errors are actionable; Carbon and Soft Sun contrast contract remains unchanged |

## Defects fixed in this release

- A Today ViewModel could remain subscribed to yesterday after midnight, or retain old day bounds
  after a time-zone change. A minute-aligned local-day key now switches every date-scoped flow.
- “Erase phone data” used the ordinary capture stop path. That path could finalize an open visit
  asynchronously after Room had been cleared, and capture-health, motion, pending-visit, diagnostic,
  session and export stores were outside the main settings file. The erase path now discards pending
  writes, attempts every cleanup even if one fails, verifies sensitive files, and reports partial failure.
- Ordinary service teardown could wait on a remote geocoder before persisting an open stay. It now
  saves immediately with a coordinate fallback; the person can name the stop later.
- Route-card tile and output bitmaps previously depended on a later GC. Fast scrolling or cancelled
  renders could retain native memory. Tiles, replaced snapshots and partial renders are now recycled,
  and implausibly large decoded tile dimensions are rejected.
- Imported high-density routes could hand an unbounded point list to Compose/MapLibre. Metrics still
  use every accepted fix, while the visual path is evenly bounded and preserves every visible gap.
- Voice input was forced to English (India). It now follows the Android locale, with the system
  default used when the locale is undefined.
- Several secondary truncation sites could split a UTF-16 surrogate pair. Search, recovery
  passphrases, geocoder labels, audit text, report context, event metadata and previews now use the
  same Unicode-safe boundary policy as primary inputs.
- Encrypted backup-session cleanup handled exceptions but overlooked `SharedPreferences.commit()`
  returning `false`; it now falls back to removing the preference file.

## Release identity

- Android package: `com.dailybeat.app`
- Android `versionCode`: `29`
- Android `versionName`: `4.1.1`
- Release marker and tag: `4.1.1` / `v4.1.1`
- APK asset: `DailyBeat-v4.1.1.apk`
- Integrity asset: `SHA256SUMS.txt`
- Permanent signing certificate SHA-256:
  `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`

## Verification contract

Local pre-release validation covers all JVM tests, Android lint, standard and Google-free debug plus
instrumentation APK assembly, Google-dependency exclusion, repository policy tests, and a disposable
Android 14 device run of the reliability suite. The protected remote matrix repeats both build
variants and both offline instrumentation lanes, then adds live/native backup recovery, executable
RLS isolation, release policy, CodeQL, dependency review and OSS vulnerability/secret scanning on
the exact release commit. The publisher waits for those checks before tagging or attaching an APK.

Local result: 288 JVM tests and 67 repository/release-policy tests passed; Android lint and APK/test
APK assembly passed in both variants; the Google-free dependency gate accepted 91 resolved
artifacts; and the disposable Android 14 runs completed 38 standard-build tests (one
environment-dependent lifecycle case skipped by its precondition) plus 37 Google-free tests with
zero failures.

## Honest limitations

- GPS is an observation aid, not proof of a person's activity or an evidentiary chain of custody.
- The standard build can sleep active location work through Google activity transitions. The
  Google-free build retains its conservative platform location profile; battery behavior remains a
  physical-device field measurement, not a universal drain guarantee.
- The 14-day battery/stop-recall trial and independent security/key-lifecycle review remain external
  acceptance work. This release does not claim that an open-source store has accepted the app.
