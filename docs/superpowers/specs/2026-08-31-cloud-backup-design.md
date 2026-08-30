# DailyBeat Cloud Backup Design

## Goal

Let a DailyBeat user move to another Android phone without losing diaries, events, places, visits, or non-secret settings. The launch target is a small, dependable backup/restore feature rather than a multi-device collaborative sync engine.

## Considered approaches

1. **One versioned snapshot per user in Supabase (selected).** Simple atomic backup and explicit restore, with a small schema and clear failure behavior.
2. Normalized bidirectional record sync. Better for simultaneous multi-device editing, but requires stable global IDs, tombstones, conflict resolution, and substantially more migration risk.
3. Android automatic backup only. Minimal code, but restoration depends on Google device-backup behavior and is difficult to verify for a GitHub-distributed APK.

## Architecture

Supabase Auth provides email/password accounts. A `dailybeat_backups` table stores one JSON snapshot per authenticated user. Row-level security restricts each user to their own row. Android continues to use Room as the source of truth; cloud operations are explicit and never block normal capture or diary use.

The app communicates with Supabase Auth and PostgREST using the existing OkHttp dependency. The public Supabase URL and anonymous key are build configuration, not secrets. User access and refresh tokens are stored with Android encrypted preferences. The DeepSeek/API-provider key is never included in a backup and must be entered again on a new phone.

## Data flow

- Sign in: email/password is exchanged for a Supabase session; the password is never persisted.
- Backup: Room entities and non-secret settings are serialized into a versioned JSON snapshot, then atomically upserted into the authenticated user's row.
- Restore: the snapshot is downloaded, schema-validated, and applied inside one Room transaction. Existing local data is replaced only after the user explicitly chooses restore and the complete remote snapshot has validated.
- Automatic safety backup: after a successful manual sign-in/backup, WorkManager may refresh the existing snapshot periodically when networking is available. Automatic restore is forbidden.
- Sign out: cloud session tokens are removed; local data remains untouched.

## User experience

Settings contains a `Cloud backup` card with email/password fields when signed out and status, `Back up now`, `Restore`, and `Sign out` controls when signed in. It shows the last successful backup timestamp and concise actionable errors. Restore requires an explicit confirmation because it replaces local records.

If Supabase is not configured in the build, the card says cloud backup is unavailable in this build; the rest of DailyBeat remains fully functional.

## Failure and privacy rules

- No Room data is deleted before a complete remote snapshot passes validation.
- A failed upload or download leaves local data unchanged.
- API keys, auth passwords, audit logs, generated PDFs, and geocode cache entries are excluded.
- Supabase row-level security is enabled and tested in SQL.
- Logs and UI errors never contain passwords, access tokens, refresh tokens, or API keys.
- Restore supports only known snapshot versions and refuses malformed or future versions.

## Verification

- Unit tests cover serialization, secret exclusion, malformed snapshots, auth/session parsing, and restore validation.
- Room tests prove backup/restore round trips and transactional replacement.
- Android instrumentation tests cover signed-out/cloud-unconfigured UI without contacting production services.
- A real Supabase smoke test is required before calling cloud backup operational.
- Existing unit, instrumentation, release build, and connected-phone QA must remain green.

## Launch boundary

Version one supports one authoritative snapshot per account and deliberate phone migration. Concurrent edits on multiple phones, record-level merging, media backup, and server-side DeepSeek proxying are outside this launch scope.
