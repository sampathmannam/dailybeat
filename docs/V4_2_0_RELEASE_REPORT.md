# DailyBeat 4.2.0 release evidence

Version 4.2.0 (code 32) contains the reliability and privacy hardening described in
[the hardening report](hardening/2026-09-19.md). Production publication was explicitly
requested after delivery of the QA build on 2026-09-19. The package remains
`com.dailybeat.app` and the permanent signing identity is unchanged.

## Changes

- Room schema 10 adds a durable capture journal, atomic processing checkpoints and retry recovery.
- Observed stays survive battery sleep and capture shutdown. Google location startup has a bounded
  timeout and an Android location fallback; permission failures remain visible.
- All overlapping private zones are checked before address lookup, including immediately before
  network dispatch. Managed address lookup is opt-in. Cloud report labels exclude raw coordinates.
- Encrypted, authenticated archive pages allow bounded-memory backup and transactional restore,
  retain five completed versions, and preserve recovery of legacy encrypted backups.
- Account deletion verifies the caller with Auth and requires password authentication within five
  minutes; token refresh alone does not qualify.
- Days uses batched queries, paging and date navigation. Large text reflows. Support sharing requires
  a content-free preview. Android 16/SDK 36 and native 16 KiB alignment are checked.

## Backend deployment

On 2026-09-19, the production SQL editor applied only
`20260919090000_versioned_backup_archives.sql` in an explicit transaction with 5-second lock and
30-second statement timeouts. Preflight confirmed the two existing backup tables and absence of
the new archive tables. Existing backups were not rewritten. Postflight confirmed RLS on both new
tables, no anonymous SELECT, and no direct authenticated INSERT. All three archive RPCs use an
empty search path, deny anonymous execution and allow authenticated execution with owner checks.

The existing `delete-account` Edge Function was updated with the checked-in `index.ts` and
`handler.ts` modules. The dashboard confirmed deployment and JWT verification remained enabled.
An unauthenticated production POST returned HTTP 401. No real user account was deleted during
verification. The ten handler tests cover recent/old/refreshed authentication and failure paths;
a destructive production account-deletion drill was not performed.

The later 2026-09-19 operational verification found that the legacy size-limit/privilege migration
had not reached production, despite passing local tests. That missing hardening was applied and
verified, then all four tables and four functions matched a fresh migration-built database.
The three missing migration-history entries were reconciled only after that match; stored source
hashes matched the repository and the original history entry was preserved. No backup payload was
rewritten or deleted. See the [operational verification](hardening/2026-09-19-operations.md).
The hosted password policy was also aligned with the repository. Production email delivery still
needs custom SMTP; the default test mail service is insufficient for general public signup.
Do not blindly replay old migrations or reset production. See [operations](hardening/OPERATIONS.md)
for deployment and recovery procedures.

## Validation and publication contract

The hardening source at `9dfea15f13e186f075aa4fe757ac948a3d2cebdf` passed 321 JVM tests per build
variant, 70 repository policy tests, lint with zero errors, ten Deno tests, 40 pgTAP assertions and
an isolated authenticated archive HTTP smoke test. Local Android 16 ran 41 standard instrumentation
tests, 14 focused FOSS tests and a real Google-services outage/fallback test. API 34 CI ran both
41-test instrumentation lanes. Archive recovery includes 80,000 records, wrong-passphrase and
tampering rejection. These are QA results, not long-duration physical-device measurements.

For this version, the native live-cloud gate additionally creates an encrypted archive, reconstructs
the client, rejects a wrong recovery passphrase, restores the selected version, and verifies cleanup
of only its own synthetic fixture. It refuses to run against a nonempty archive store and preserves
the previous legacy encrypted QA backup. Credentials remain in protected CI secrets.

The version-specific policy suite passed (70 tests), and the extended FOSS instrumentation source
compiled locally. The protected release commit must independently pass standard/FOSS build,
unit/lint, both API 34 lanes, API 36 native live recovery, live protocol, backend RLS and security
checks. The publisher waits for those exact-commit checks and verifies the permanent certificate:

`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`

The workflow publishes immutable `DailyBeat-v4.2.0.apk` and `SHA256SUMS.txt` assets. The release page
and workflow runs are the authoritative final publication evidence. Obtainium should update the
existing installation without uninstalling it.

## Remaining operational evidence

Battery consumption, stay-detection recall and OEM background restrictions still require a
multi-day physical-phone trial. No independent penetration audit, uptime SLA or fleet-scale
performance certification is claimed. Optional AI drafts still require user review. Preserve local
history and the recovery passphrase when updating.
