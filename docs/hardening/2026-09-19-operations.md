# Operational verification after v4.2.0

The production app remains v4.2.0, code 32, commit
`b62ba9e9914fbbed337ef21ca59869b335007a5e`. This follow-up changes backend state, operational
tools and verification; it does not require another APK or an uninstall.

## Production correction and verification

The live database was checked through the authenticated Supabase dashboard. The migration ledger
initially contained only `202608310001`. This was more than a tracking mismatch: the legacy backup
table lacked the validated 12 MiB size constraint and still granted authenticated users TRUNCATE
and TRIGGER. Local migration tests had passed because they build a fresh schema; they had not
verified whether those changes were deployed. The privilege finding is a real hardening gap;
no claim of observed exploitation or data loss is made.

The legacy migration was applied in a transaction with a five-second lock timeout and thirty-second
statement timeout. A preflight aggregate found no oversized legacy rows. Postflight verified
TRUNCATE=false, TRIGGER=false and the validated size limit. No existing backup payload was changed.

The metadata-only query in `scripts/production_schema_fingerprint.sql` then matched all eight
components from a fresh local database: four tables and four functions. It compares columns,
constraints, indexes, row policies, grants, triggers, function definitions, search paths and execution
permissions. The three missing ledger entries were inserted in a guarded transaction with the
exact migration source, while preserving the original entry. The schema still matched afterward.

| Recorded version | Migration | Recorded source MD5 matches repository |
|---|---|---|
| 202609110001 | dailybeat_backups_hardening | 631255e012be0eff344c733f655066ce |
| 202609160001 | encrypted_backups | cdb402f4e503357b9303d7a366132a2d |
| 20260919090000 | versioned_backup_archives | 776f7eead5b9311f31c1d662b0af44d6 |

MD5 here compares database text; it is not a signature. Reviewed migration SHA-256 hashes and the
schema component fingerprints are stored in `supabase/schema-baseline.json` and checked in CI.

## Hosted authentication configuration

A separate dashboard check found minimum password length 6, no required character classes and
secure password changes disabled. Production now matches the reviewed repository policy: minimum
12 characters, lowercase/uppercase/digits/symbols, and secure password changes enabled. The saved
values were verified after reloading the dashboard. This changes policy, not anyone's password.
Existing passwords remain usable for sign-in; new/changed passwords must meet the stronger rules.
[Supabase password-policy behavior](https://supabase.com/docs/guides/auth/password-security).

Email confirmation and secure email changes were already enabled; anonymous sign-in and manual
identity linking were disabled. Sign-in/sign-up and verification limits were 30 requests per five
minutes per IP, and refresh was 150, matching the repository. Production's eight-digit OTP is
stronger than the six-digit local test setting and was preserved. Paid leaked-password protection
is unavailable on the current plan. No key was rotated and no password/account was changed.

Custom SMTP was disabled, with the default email limit of two per hour. Supabase restricts its
default email service to authorized team recipients and does not intend it for production.
Public signup and email recovery therefore remain blocked on a verified sending domain/provider;
turning off email confirmation is not an acceptable workaround.
[Supabase email-delivery requirements](https://supabase.com/docs/guides/auth/auth-smtp).

## Recovery and field-trial tooling

The local recovery drill created a synthetic Auth account and legacy, encrypted and eight-page
archive fixtures, dumped the application/Auth schemas, and restored them into a brand-new isolated
database. Auth records and backup payload hashes matched, schema/grants matched, the owner could
read all eight pages, and another identity could read none. The temporary database and account
were removed and cleanup verified. This test now runs in the Backend RLS workflow.

This is evidence for the restore procedure on a pre-provisioned local Supabase cluster. It does
not establish production backup availability, disaster recovery time, encryption-key recovery,
provider outage recovery or performance at production scale.

The field collector now correctly parses Android's AC/USB/wireless flags and rejects emulators.
The analyzer excludes unknown/charging states and no longer bridges intermediate charging,
phase changes, upgrades, backend changes or reboots. False-positive stops no longer inflate the
recall denominator. Raw trial files are private and ignored by Git.

Local validation: 93 Python tests passed; 40 pgTAP assertions passed; the schema comparison and
the complete synthetic restore passed. The protected PR's required CI results remain authoritative
for the merged change.

## Open acceptance evidence

| Gate | Status on 2026-09-19 | Required completion evidence |
|---|---|---|
| Production migration state | Verified and reconciled | Four recorded versions and eight matching components |
| Hosted password/auth policy | Corrected and verified | Minimum 12, character classes, secure password changes; existing protective settings preserved |
| Production authentication email | Custom SMTP is off; default service is restricted | Configured provider/domain and authorized signup/recovery delivery tests |
| Synthetic database recovery | Passed locally; added to CI | Matching Auth/data/schema plus verified isolation and cleanup |
| Production account deletion | Script and guard tests ready; awaiting secure administrative credential hand-off | Newly generated accounts only; fresh/old session checks, cascade and sentinel verification |
| Physical battery/capture trial | Awaiting physical ADB connection; emulator excluded | 14 days of real observations and independently recorded stops |
| Production database backups | Dashboard showed Free plan and no managed backups | Approved provider backup plan or encrypted off-site exports plus a real restore exercise |
| Incident responsibility | Runbook prepared; named owner and contact not confirmed | Accepted owner, escalation contact and response expectations |
| Independent security review | Review packet prepared; no reviewer appointed | Separate reviewer report, remediation and retest |

These open gates prevent an unqualified “enterprise-grade” certification. They do not undo the
successful v4.2.0 release, but they must not be represented as completed by code tests alone.
