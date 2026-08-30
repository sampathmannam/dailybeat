# DailyBeat Cloud Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a migration-safe Supabase account backup and restore flow for DailyBeat before the next stable APK release.

**Architecture:** Room remains authoritative. A versioned JSON snapshot is uploaded to one RLS-protected Supabase row per user through Auth/PostgREST; restore validates the entire payload before replacing local records in one transaction. The existing OkHttp client and encrypted preferences are reused.

**Tech Stack:** Kotlin, Room, OkHttp, Android Security Crypto, WorkManager, Jetpack Compose, Supabase Auth REST/PostgREST, PostgreSQL RLS.

## Global Constraints

- Never upload or log the DeepSeek/API-provider key, password, Supabase session tokens, audit logs, PDFs, or geocode cache.
- Never erase local records until a complete supported snapshot has validated.
- Automatic backup may update remote state; automatic restore is forbidden.
- Builds without Supabase configuration must keep every existing local feature working.
- Version one is a single-user snapshot backup, not record-level multi-device synchronization.

---

### Task 1: Versioned snapshot codec

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/backup/BackupSnapshot.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/BackupSnapshotCodec.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/backup/BackupSnapshotCodecTest.kt`

**Interfaces:**
- Produces: `BackupSnapshotCodec.encode(snapshot): String` and `decode(json): BackupSnapshot`.

- [ ] Write tests proving a full round trip, API-key absence, and rejection of malformed/future-version payloads.
- [ ] Run the focused test and confirm it fails because the codec is absent.
- [ ] Implement the minimal version-1 data classes and `org.json` codec.
- [ ] Run focused and existing unit tests; commit.

### Task 2: Transactional local snapshot store

**Files:**
- Modify: `android/app/src/main/java/com/dailybeat/app/data/db/EventDao.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/data/db/VisitDao.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/data/db/PlaceDao.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/data/db/DiaryDao.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/LocalBackupStore.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/backup/LocalBackupStoreTest.kt`

**Interfaces:**
- Consumes: `BackupSnapshot`.
- Produces: `createSnapshot()` and `restore(snapshot)` with one Room transaction.

- [ ] Write a Room test proving round-trip preservation and all-or-nothing replacement.
- [ ] Run it and confirm failure because the store/query methods are absent.
- [ ] Add export/upsert/delete DAO queries and implement the store.
- [ ] Run Room and repository tests; commit.

### Task 3: Supabase session and backup client

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/BackupConfiguration.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/BackupSessionStore.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/SupabaseBackupClient.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/backup/SupabaseBackupClientTest.kt`

**Interfaces:**
- Produces: sign-in, refresh, sign-out, upload, and download operations returning typed results.

- [ ] Write fake-server tests for successful auth/upload/download, token refresh, HTTP errors, and secret-safe messages.
- [ ] Run and confirm failure because the client is absent.
- [ ] Expose URL/anon key through empty-safe BuildConfig fields, persist tokens encrypted, and implement REST calls.
- [ ] Run focused and full unit tests; commit.

### Task 4: Backup coordinator and Settings UI

**Files:**
- Modify: `android/app/src/main/java/com/dailybeat/app/DailyBeatApp.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/backup/BackupCoordinator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt`

**Interfaces:**
- Consumes: local store and Supabase client.
- Produces: signed-out/signed-in/loading/success/error UI state and explicit backup/confirmed restore/sign-out actions.

- [ ] Write ViewModel/unit and instrumentation tests for unconfigured, signed-out, backup-success, restore-confirmation, and error states.
- [ ] Run and confirm the new assertions fail.
- [ ] Implement coordinator wiring and the minimal Settings card.
- [ ] Run unit and instrumentation tests; commit.

### Task 5: Backend schema and deployment contract

**Files:**
- Create: `supabase/migrations/202608310001_dailybeat_backups.sql`
- Create: `supabase/tests/dailybeat_backups_rls.sql`
- Modify: `.github/workflows/android-release.yml`
- Modify: `docs/RELEASE.md`
- Test: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Produces: RLS-protected `dailybeat_backups` table and release-time Supabase build configuration.

- [ ] Write failing repository checks for RLS, owner-scoped policies, secret names, and no hard-coded credentials.
- [ ] Run and confirm failure because migration/workflow wiring is absent.
- [ ] Add idempotent SQL, workflow secret wiring, and deployment instructions.
- [ ] Run repository tests; commit.

### Task 6: End-to-end verification and release

**Files:**
- Modify: version fields in `android/app/build.gradle.kts` only after verification.

**Interfaces:**
- Consumes: authenticated Supabase project and release-signing secrets.
- Produces: pushed commit, passing GitHub Actions run, signed APK, checksum, and GitHub release.

- [ ] Apply the migration to an authenticated Supabase project and run an account-isolation smoke test.
- [ ] Back up synthetic data on one QA install, replace its local records, restore, and compare counts/content.
- [ ] Run all unit, lint/build, connected-device, and guarded release-pipeline tests.
- [ ] Increment to the next patch version, build the signed release APK, verify signature/checksum, commit, push, tag, and monitor GitHub Actions.
- [ ] Publish the stable GitHub release only if every required check passes.
