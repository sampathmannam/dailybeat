# DailyBeat

A phone-first Android journal for remembering where your day took you, adding notes, and reviewing a daily record.

Personal, Field work and Police templates share one app. The Police template is a writing format,
not certification of attendance, official submission, or an evidentiary chain of custody.

## Current release

**4.2.1 / code 33** is the current production release line. It upgrades the permanently signed
`com.dailybeat.app` package used by GitHub Releases and Obtainium.

- Personal / Field work / Police onboarding and settings; existing installations retain Police.
- Deterministic, source-linked daily drafts without an account or AI; optional cloud drafting.
- On-device search across saved notes, diary text and visible recorded places.
- Sharing previews for PDF and seven-day ZIP, checked against current records and privacy settings.
- Conservative sharing copies when privacy controls are active; saved diary prose stays unchanged.
- Paged client-encrypted recovery with five completed versions, a separate recovery passphrase, and explicit legacy restore.
- Durable capture replay, privacy checks that fail closed, and platform fallback when Google location startup stalls.
- Restorable diary checkpoints, immutable visit-correction history, and optional local retention.
- Separate controls to delete cloud backups, the cloud account, or every local DailyBeat record.
- Midnight and time-zone rollover without reopening Today; bounded, memory-safe route rendering.
- Verified local erasure of records, capture checkpoints, credentials, diagnostics, and exports.
- Monday–Sunday Insights, truthful low-power movement-watching status, and a no-badge
  passive-capture notification.
- Direct date selection in Days without redundant older/newer paging controls.
- Four focused Settings categories with a pinned back action instead of one continuous page.
- Standard Google location backend and a separate Android-platform-only build.
- Warm Butter accents and neutral Carbon dark mode; no DSR features added.

See [implementation and release plan](docs/PUBLIC_RELEASE_PLAN.md),
[validation evidence and field protocol](docs/PUBLIC_BETA_VALIDATION.md),
[privacy information](PRIVACY.md), [offline Tamil Nadu maps](docs/OFFLINE_MAPS.md), and the larger [product roadmap](docs/PRODUCT_IMPROVEMENT_PLAN.md).

## Build and test

Requires JDK 17+, Android SDK 36, and the checked-in Gradle wrapper. Set JAVA_HOME and ANDROID_HOME
for your installation; do not commit local machine paths or credentials.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew testDebugUnitTest lintDebug assembleDebug verifyGoogleFreeDependencies -PdailybeatFoss=true
```

The second command excludes Play Services/Firebase dependencies and selects Android location APIs.
Both commands produce an isolated QA package, not an update to production. They share a build output
path, so copy each artifact before building the other configuration. Platform-only capture does not
use the Google activity-transition idle-sleep mechanism; its battery performance requires separate
field measurement.

Instrumentation fixtures are destructive **only inside** `com.dailybeat.app.qa.e2eloop`. Use a dedicated
emulator and an explicit serial. Never run broad connected tests with a user's phone attached.

```bash
./gradlew assembleDebug assembleDebugAndroidTest -PdailybeatFoss=true -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop
# Install and invoke tests with adb -s YOUR_DISPOSABLE_EMULATOR_SERIAL.
```

Repository policy checks:

```bash
python3 -m pytest scripts/tests/ -q
```

CI has separate standard and Google-free builds, Android instrumentation, security checks, and live
legacy/encrypted-envelope transport checks. A passing dependency gate is not F-Droid acceptance or
a complete third-party licence audit.

## Everyday use

1. Choose a template; a name is optional.
2. Enable location permissions only if you want background journey capture. Notes and diaries work without them.
3. Add a moment or a text note. Use Days to review, name or hide stops, search records, or browse older dates.
4. Generate a local draft, edit it, then inspect the sharing copy before choosing a destination.
5. Cloud AI is optional and off by default. A configured provider receives selected journal text.
   Unlinked custom text and voice enrichment stay local while privacy controls are active.
6. Saved place names work offline. Automatic place lookup is off unless you configure a managed
   HTTPS endpoint in Settings; coordinates within any private zone are never sent.

Old automatic-report preferences require a fresh opt-in; restoring a backup never re-enables the
automatic report. GPS is an observation, not proof of what someone did at a place. Cloud citations
are structural links and do not prove that a model's claims are true.

## Backup and recovery

Deploy the [archive migration](supabase/migrations/20260919090000_versioned_backup_archives.sql) and
updated `delete-account` function before releasing this branch. The archive path streams bounded
database pages, compresses them, and encrypts each page with AES-256-GCM. Its authenticated manifest
binds every page to one version. Five completed versions remain available in Settings → Backup
history. Failed uploads preserve the previous completed backup. Recovery validates all pages first
and restores records in one transaction. The passphrase is never uploaded or saved in settings;
losing it makes recovery impossible. Keep it in a password manager.

Previous v1 encrypted backups remain readable. Unencrypted legacy restore is explicit. Neither is
removed automatically by an archive upload. Settings can delete all formats. Account deletion uses
a verified Supabase identity and requires a password authentication event within five minutes;
refreshing an old session does not satisfy that requirement. The previously deployed function
predates this hardening and must be updated as part of rollout. See the
[hardening implementation and validation](docs/hardening/2026-09-19.md).

API keys, recovery passphrases, auth sessions and dormant DSR records are excluded from snapshots.

The app packages its GPL text and offline third-party notices. The coordinate-level POM inventory
remains evidence rather than legal clearance for every native component or asset.

## Updating safely

Production package: `com.dailybeat.app`. Never uninstall it to resolve a signing mismatch.
Production releases must keep the permanent signing certificate and pass the existing release gates.
Only the signed APK attached to the matching GitHub Release is an in-place production update.

The latest stable release is available through [GitHub Releases](https://github.com/sampathmannam/dailybeat/releases).
The remaining dependency/asset audit, F-Droid review and store-specific signing strategy apply to
future third-party store submissions; they do not change the GitHub/Obtainium update identity.
The Google-backed build includes dependencies that need separate compatibility review; do not
treat the project licence as permission to redistribute every dependency under that licence.

## Licence

DailyBeat original source is licensed under **GNU GPL version 3 only** (`GPL-3.0-only`),
as approved by the project owner on 16 September 2026. See [LICENSE](LICENSE).
Third-party code and assets retain their own licences and notices. No additional linking exception
or grant over third-party material is implied. The Google-free configuration is the candidate for
FLOSS distribution, subject to the remaining dependency/asset audit and release gates.

## Scope

DailyBeat is a voluntary personal record, not a covert tracker, employer surveillance console,
emergency dispatch tool, or certified police evidence system. DSR/PatrolGrid remain separate;
dormant compatibility tables are preserved.

Product: [PRODUCT.md](PRODUCT.md) · Design: [docs/DESIGN.md](docs/DESIGN.md) ·
Security baseline: [docs/SECURITY_HARDENING.md](docs/SECURITY_HARDENING.md)
