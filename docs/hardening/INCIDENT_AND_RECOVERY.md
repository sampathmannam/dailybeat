# Incident and recovery procedures

## Ownership and response

The repository owner must confirm an incident owner, a backup operator and an escalation contact.
Until those people accept the responsibility, there is no staffed response SLA. Keep contact
details in the private operational record, not this public repository. This document does not
subscribe anyone to alerts or send messages on their behalf.

| Severity | Trigger | Immediate response |
|---|---|---|
| Critical | Reproduced cross-user access, unauthorized deletion, confirmed credential leak or private-location transmission | Halt the affected rollout/integration, preserve minimal evidence, contain the specific access path, and bring in the owner |
| High | Repeated backup/restore failure, account deletion failing, capture history loss, app crash loop | Preserve phone history and existing archives, reproduce on an isolated fixture, identify the affected versions and ship a checked fix |
| Normal | Isolated device restriction, cosmetic problem or recoverable provider failure | Capture the redacted support preview, record device/version and follow the relevant troubleshooting path |

For each incident record discovery time in UTC, affected release/backend revision, observable
impact, scope confidence, owner, containment actions, recovery checks and next update time. Never
include diary text, raw coordinates, session tokens, credentials or a user's database in an issue.
Use a private channel for sensitive findings; public GitHub issues are not a secret store.

## Containment and recovery sequence

1. Verify the failure using a disposable fixture. Preserve the user's app data and existing cloud
   versions. Do not clear storage, uninstall, reset a database or delete backups as troubleshooting.
2. Halt a faulty APK rollout and optional failing integration. Android database downgrades are not
   a rollback plan; release a forward correction under the same signing identity and higher code.
3. For a backend failure, inspect exact deployed migration/function versions and schema fingerprints.
   Fix a missing deployment separately from ledger metadata. Do not weaken RLS or authentication
   to get a test passing. Rotate an exposed privileged key through the provider's supported process
   and verify dependent services; ordinary operational checks do not require key rotation.
4. Restore a database backup into an isolated environment first. Verify Auth compatibility, backup
   rows, constraints, RLS, RPC ownership checks, deletion cascades and the ability of a disposable
   native client to recover encrypted pages with its known passphrase. Keep plaintext exports
   private and encrypted at rest. User recovery passphrases are not held by the service.
5. A production restore requires a reviewed restore point and an explicit data-loss/cutover plan.
   Database snapshots can resurrect deleted accounts and backups: reconcile all post-snapshot
   erasure requests before reopening service. Never treat a pre-deletion snapshot as permission
   to reinstate erased data. If no trusted deletion record exists, stop the cutover and escalate.
6. Verify the fixed release/backend with synthetic accounts, then monitor the agreed pilot. Close
   only after the owner records the cause, affected scope, verification and preventive action.

## Production backup acceptance

On 2026-09-19 the production dashboard showed the Free plan and no managed backups. Five retained
app archives are in the same database; they protect against a bad individual upload but do not
provide database disaster recovery.

Choose and provision one of these approaches before claiming recoverability:

- Managed provider backups: confirm the actual project/organization checkout cost, retention,
  restore procedure, access controls and restore history. Supabase currently documents daily
  backups with seven days of retention on Pro. [Provider backup documentation](https://supabase.com/docs/guides/platform/backups).
- Self-managed backups: an authorized operator runs provider-supported database exports with
  properly scoped credentials, encrypts them before off-site storage, protects the recovery key
  separately, checks checksums/upload success and enforces a reviewed retention policy. Include
  Auth and required schema/roles/configuration in the recovery plan. An API export of backup rows
  is not a full database/Auth backup. Supabase recommends regular exports for Free projects.
  [Provider backup documentation](https://supabase.com/docs/guides/platform/backups).

Set a recovery-point target and recovery-time target with the owner, then measure them with an
actual restore. Suggested initial evaluation targets are at most 24 hours of database loss and
four hours to restore service; these are unproven planning targets, not promises. The local
subsecond synthetic restore is not a production timing estimate. Test off-site access, decryption,
provider provisioning and deleted-account handling, not just SQL import. Repeat after schema/Auth
changes and on the agreed cadence. Track backup failures and overdue restore tests privately.

## Hosted authentication and email acceptance

Production now enforces the repository's 12-character password policy with character classes and
secure password changes. Confirm those settings after deployments; local `supabase/config.toml`
does not configure the hosted project. Preserve email confirmation, secure email changes,
disabled anonymous/manual linking, the eight-digit production OTP and the checked request limits.

Custom SMTP is still unconfigured. The default Supabase email service permits only authorized
team recipients, is limited to two messages per hour and has no production delivery SLA.
[Provider requirements](https://supabase.com/docs/guides/auth/auth-smtp).

The owner must select an existing provider and verified sending domain or authorize provisioning.
Then configure host, TLS port, username, existing SMTP credential, sender address and sender name
through the authenticated Supabase settings. Keep credentials in provider-managed settings, never
in an APK, Git file or public CI artifact. Verify the provider's domain authentication records,
sender authorization, applicable quota and delivery monitoring. Use only explicitly designated
test inboxes to exercise signup confirmation and recovery/reauthentication. Record delivered,
expired and reused-link behavior before accepting email readiness. Do not weaken email confirmation
or add people to the Supabase organization merely to bypass restricted default delivery.

## Scoped production account-deletion drill

`scripts/live_account_deletion_drill.py` requires the explicit production project identifier and
private files containing the public client key and an existing administrative key. It creates two
fresh UUID accounts with random markers; it never reuses a person or protected CI account. Do not
paste privileged keys into chat, commits or command arguments. Keep temporary credential files
mode 0600 in a directory outside the repository and remove the temporary copy after verified cleanup.

```sh
python3 scripts/live_account_deletion_drill.py \
  --allow-production-project mrhffxtuzxqzcqicchoj \
  --public-key-file /PRIVATE/PATH/public-client-key \
  --admin-key-file /PRIVATE/PATH/existing-admin-key \
  --state-file /PRIVATE/PATH/deletion-drill-state.json \
  --report-file /PRIVATE/PATH/deletion-drill-report.json
```

The drill waits for real password authentication to age past five minutes, verifies that refreshing
that session does not authorize deletion, signs in freshly and verifies deletion of only the
verified caller. It checks all backup cascades and the unrelated sentinel's unchanged payloads,
then removes only its own marked fixtures. Opaque payload fixtures test deletion, not cryptography.
The native release gate separately tests encrypted recovery and wrong-passphrase rejection.

If interrupted, repeat the same command with `--cleanup-only` and the same state file. Do not start
another drill until cleanup is verified. The journal stores fixture UUIDs and markers, never
passwords or tokens. Retain only the redacted success report in release evidence.

## Local recovery regression

Backend CI automatically exercises `scripts/local_database_recovery_drill.py` on its isolated
`dailybeat-hardening-<run-id>` stack. The script accepts only loopback Auth and a matching local
container naming convention, verifies that Auth and SQL refer to the same fixture, creates a new
restore database, compares payload/schema hashes, verifies RLS and cleans up its fixture. It uses
the existing local cluster superuser to restore Auth default privileges. No remote project is accepted.
It preserves the original object owners and tests Auth service-role read/update access; removing
ownership during restore would otherwise strand Auth without its implicit table privileges.
Its private dump is not uploaded as an artifact or counted as a production backup.

Required CI groups use `queue: max` with cancellation disabled. Let live fixture work finish its
cleanup before updating or manually canceling a run. GitHub's queue is bounded; a canceled or
skipped required gate is not a success and must be rerun after its cause is resolved.
