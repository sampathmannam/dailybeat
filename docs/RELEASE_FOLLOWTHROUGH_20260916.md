# Release follow-through — 16 September 2026

This supersedes the earlier access/licence/device blockers in the release-attempt record.
**No new production release has been cut.** Candidate remains 4.1.0-beta.1 / code 28;
production remains 4.0.6 / code 27.

## Resolved with owner input

- The owner approved **GPL-3.0**. The unmodified GNU licence text is now included as `LICENSE`,
  README specifies `GPL-3.0-only` for original source, and Python package metadata includes it.
  Third-party licences remain separate; no linking exception was requested or added.
- The owner signed in to the Supabase dashboard and expressly approved the additive migration.
  A read-only check of the DailyBeat project confirmed that `dailybeat_encrypted_backups` was absent
  and `dailybeat_backups` plus `set_dailybeat_backup_updated_at()` were present.
- Applied `supabase/migrations/202609160001_encrypted_backups.sql` in one transaction with a five-second
  lock timeout, then notified PostgREST to reload its schema. The migration does not read or modify
  the legacy backup table. Its SHA-256 is
  `83d79daacb4498517467c93b0cd50b9820c9006c247622bae75127074abd3df5`.
- The live metadata check returned: table present, RLS enabled, anonymous SELECT/INSERT denied,
  and four policies. The previously failing GitHub job was rerun successfully:
  **legacy and encrypted-envelope transport/cleanup gates passed** at 11:44:41 UTC.
  [Verified job](https://github.com/sampathmannam/dailybeat/actions/runs/35089390842/job/104780071195).

The migration was applied through the approved dashboard session, not the still-unauthenticated CLI.
No migration-history repair was performed. Inspect and reconcile CLI migration history before a later
`supabase db push`; do not blindly replay this CREATE TABLE migration or reset the production database.

## Physical phone evidence

On the connected Motorola Signature (Android 17), explicitly targeted disposable package
`com.dailybeat.app.qa.e2eloop` only:

- Standard configuration: **36 tests passed**, no failures/errors/skips. The added test verifies
  that choosing shorter retention explains permanent deletion before changing the setting.
- Google-free configuration: **35 tests passed**, no failures/errors/skips.
- The expanded **36-test Google-free matrix** also passed on the dedicated Android 14 / API 34 AVD;
  this includes the retention-warning regression added after the physical-phone run.
- A deliberate negative test requested mandatory cloud recovery without credentials. It failed
  with `Required live backup credentials were not provided.` and zero skips, as required.
  This is verification of fail-closed behavior, not a successful cloud recovery test.
- Production `com.dailybeat.app` still reports v4.0.6 / code 27 and its original update time.
  No production installation, uninstall, data clear, permission grant or battery-stat reset occurred.

The configurations exercise the same shared suite rather than distinct product behaviors. The
Google-free phone pass predates the added retention-dialog test, while its final 279-test host suite,
36-test emulator suite and APK/lint gates pass. Both device matrices exclude the live Android cloud
test. USB-connected tests and emulators are not a battery field trial.

## Additional test hardening

The previous Android cloud test could skip because it expected an unprovided recovery passphrase.
It now generates its own random fixture passphrase in memory. Supplied credentials or a mandatory
request cannot silently skip on missing build configuration. The shell entry points pass the mandatory
flag, and the physical-phone script rejects missing required configuration before touching a device.

The live test now preserves and verifies the dedicated QA account's original encrypted snapshot,
checks wrong-passphrase rejection, and recovers through newly constructed clients. It stops QA capture
and rejects unexpected non-fixture records before upload. Cleanup never targets the legacy table;
if a different snapshot is observed, it stops instead of overwriting that snapshot. Use an exclusive
dedicated QA account, not an everyday account or concurrent test sessions.

The later protected `native-backup` job completed the revised Android network recovery path on a
Google-free Android 15 emulator: one mandatory test ran, with zero skips and zero failures. It used
the isolated QA account and serialized cleanup. This validates that job's path; it does not make
cloud storage anonymous or turn the beta into a general release.

## Licence inventory and remaining release gates

[The dependency declaration review](DEPENDENCY_LICENSE_REVIEW.md) covers all 93 standard / 89 Google-free
runtime module POMs, including one inherited licence. The standard build has four Android SDK-licensed
Google modules absent from the Google-free build. This is not full native-component or asset clearance,
and the standard publisher has not been silently switched to a new capture backend.

Follow-up source now implements local retention, device/cloud/account deletion, diary checkpoints,
visit-correction audit records, encrypted backup/restore of those histories, offline GPL/Apache/BSD
notices, and reproducible battery/recall collection tools. The account endpoint is intentionally an
Edge Function so the APK never contains an admin key; deployment and a non-destructive authentication
probe must be verified separately.

Still open: exact-distribution native/asset clearance, independent security/key-lifecycle review,
the 14-day physical battery/recall trial, and the remaining provenance/coverage/follow-up roadmap.
Neither milestone completes the entire product roadmap or approves a store release.
