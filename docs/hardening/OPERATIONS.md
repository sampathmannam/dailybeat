# DailyBeat operational acceptance and recovery

This branch is ready for release only after the exact commit passes both build variants, unit/lint,
API 34 and API 36 instrumentation, backend pgTAP/HTTP tests, CodeQL, dependency review and the
existing live backup gates. Keep the permanent production signing certificate. Publish a new
version once approved; never overwrite a release asset or ask a user to uninstall production.

## Deployment order

1. Run the archive migration against staging and execute the checked-in pgTAP and HTTP smoke tests.
   The smoke script accepts only an isolated localhost endpoint and uses a disposable account.
2. Apply `20260919090000_versioned_backup_archives.sql` to the production project using its existing
   authenticated deployment process. It adds tables/functions and does not rewrite existing backups.
3. Deploy `supabase/functions/delete-account` with JWT verification enabled. Test denied requests,
   refreshed old sessions and a fresh sign-in using a disposable account, never a real user account.
4. Build a QA APK from the protected commit. Prove v1 recovery, archive recovery on a fresh install,
   page tampering rejection, old-version selection and deletion of only the intended QA data.
5. After QA approval, use the existing release workflow and signed release assets. Start with a small
   consenting pilot. Roll back by halting rollout; do not downgrade databases or signing identities.

Completed archives are immutable. The server retains five completed versions and at most two
unfinished uploads. Unfinished versions older than a day are reclaimed when beginning the next
upload. The app tries to delete only its failed, unpublished version; uncertain successful
publication remains safe. A failed restore leaves local tables unchanged. A forgotten recovery
passphrase cannot be recovered by support. Try a known passphrase against an older version before
changing or deleting existing backups. Do not delete old backups merely to diagnose a failed restore.

## Reliability targets to validate

These are proposed pilot acceptance thresholds, not measured production promises:

- No reproduced cross-user read, write or deletion; no private-zone network lookup in regression tests.
- No duplicate/lost committed capture after crash replay, full disk recovery or restore/erase races.
- At least 95% recall for independently annotated stays of ten minutes or longer over a 14-day trial.
- Cold-start p95 under two seconds and usable Today under three seconds on the chosen reference phone.
- Diary editing and navigation remain usable offline with 1,000 retained days; archive recovery is
  demonstrated with 80,000+ points and long text. Record dataset sizes with timings.
- Agree on a daily battery budget from baseline/control measurements before calling capture efficient.
  Compare identical OS/OEM, battery health, permissions, signal, transport and screen-on duration.

Use `scripts/benchmark_qa.py SERIAL OUTPUT.json` for repeatable launch timing. It refuses the
production package. Emulator numbers are diagnostics, not release performance claims. Use
`docs/BATTERY_FIELD_TRIAL.md` and the existing trial scripts for physical-device evidence. Re-test
foreground-only permission, activity permission denied, GMS unavailable/restarting, FOSS, Doze, reboot, time
change, forced stop, no network, weak signal and pause expiry. A force-stopped app requires user
interaction to restart; this is an Android constraint, not a background guarantee.

The standard build falls back to Android location if Google subscription startup fails or takes
more than five seconds. With precise permission it prefers the platform GPS provider during
that fallback. This preserves capture startup without assuming Play Services is healthy; compare
battery and fix quality during the physical-device trial. A registered subscription is not proof
of a fresh fix: inspect capture health and permission/device-location state as well.

## Support without telemetry

Settings → Support prepares an explicit preview of version, backend, permission booleans and a
failure count. It contains no coordinates, names, notes, auth data or diagnostic message bodies.
The user chooses a sharing destination. No background telemetry or automatic sending was added.
For a capture complaint, first verify enabled/paused state, location and notification permissions,
watcher availability and actual service state. Preserve user history while collecting evidence.

The repository owner must assign an operational owner and incident contact before a wider pilot.
For suspected location leakage, disable the managed lookup endpoint, halt rollout, preserve
content-free evidence, reproduce on synthetic data, and ship a tested correction. Never upload a
user’s raw database as a routine diagnostic step. A managed geocoder needs an agreed availability,
quota, retention and privacy contract. No public Nominatim SLA is assumed.

AI remains an optional draft assistant. Citation validation verifies structure and source IDs, not
semantic truth. Users must inspect the sharing preview and linked source records. No automated test
can certify arbitrary model prose as factually correct; do not describe AI drafts as verified evidence.
