# DailyBeat v4.3.6 — install and verify

Version 4.3.6 (Android version code **40**) is the prepared reliability and hardening update.
Publication is complete only when the protected release workflow succeeds and the signed assets
appear on GitHub Releases. The update retains package `com.dailybeat.app`, the permanent signing
identity, existing history, and the Warm Butter / Carbon appearance.

This update fixes place labels in Today/Days and offline route replay, including zero-height,
large-text, backgrounding and reduced-motion handling. Dark offline maps have clearer roads and
labels. Erase/restore invalidates stale drafts, dialogs, credentials and asynchronous writes;
privacy opt-out takes effect immediately, and interrupted capture and encrypted recovery have
additional failure-path protections. Excessively nested external JSON is rejected before recursive
parsing. Raw history, finalized visits, saved names, colours and navigation are preserved.

See the [24 September hardening report](hardening/2026-09-24.md) for the complete local evidence and
limits. The tested app code passed 615 FOSS/store unit tests, 614 standard tests plus one store-only
skip, 116 repository checks, 46 isolated backend assertions and ten account-deletion tests. Offline
Android 14/16 suites passed with documented fixture skips; Google-provider lifecycle and
denied-location startup were separately checked. Both lint configurations have zero errors and
90 existing warnings. The final versioned commit must independently pass protected CI.

The additive archive-publication migration is included as separately deployable backend hardening;
this APK release does not apply it to production. Valid current backups remain compatible. Physical
battery drain and venue accuracy still require field measurement. This remains a personal journal,
not proof of attendance, an evidence system or a guarantee of zero vulnerabilities.

The previous update prevents delayed authentication from undoing sign-out, preserves this phone's trusted
Cloud AI destination during restore, and requires fresh cloud consent afterward. Cloud requests
recheck consent and credentials before transmission and close their network calls when cancelled.
Documents move to private internal storage, with only completed exports available for sharing.
Generation checks prevent delayed reports, voice results, drafts, and exports from recreating erased
data or overwriting restored history. Capture isolates malformed fixes, recovers from corrupt or
future checkpoints, and respects changed capture consent during queued work.

See the [21 September hardening review](hardening/2026-09-21.md) for findings, test evidence, and
remaining operational limits. The release publisher independently rechecks the final main commit;
the review does not claim the app is unhackable or enterprise-certified.

## Map capabilities retained from v4.3.0

Online maps now have a device-local off switch, bounded/cancellable thumbnail transfers, configurable
providers and interactive-map retry deadlines. Safe Canvas route previews remain available during failures.
Download Tamil Nadu once in Settings → Capture & Places for offline streets, labels and available
building detail at zooms 0–15. The package is separate from the APK, defaults to Wi-Fi, resumes after
interruption and is verified before activation. Updates retain the previous working package.
See [offline map details](OFFLINE_MAPS.md) for coverage, data provenance and limitations.

## Earlier release behavior retained

Version 4.2.1 simplifies navigation without changing DailyBeat's capture, privacy, or recovery
contracts. Days keeps the direct **Go to date** picker and removes the redundant Older days / Newer
days controls. Settings opens as four focused categories, with every existing control retained and
a pinned back action that remains available while scrolling on smaller phones.

Version 4.2.0 hardens capture persistence, privacy and cloud recovery. Observations are journaled
before processing, battery sleep preserves observed stays, and a failed or stalled Google provider
falls back to Android location. Cloud recovery now stores authenticated encrypted pages and keeps
five completed versions. Automatic external address lookup is off unless a managed provider is
configured. Older dates are easier to browse and large-text values remain visible. The upgrade
uses the existing production package and signing identity, preserving local history.

Version 4.1.3 restores Today to its full capture status and summary without a **More** control. Days
cards stay compact around the map, Distance, Time out, and Auto stops, with each card's ordered place
history available through its own **More** / **Less** control. Warm Butter `#EED77B` now carries the
route, stop, launcher, identity, and yellow-action roles while neutral Carbon dark mode remains
unchanged. Low-power standby reports that Android is watching for movement instead of saying capture
is off. The release workflow also refuses to replace an already published APK or checksum.

Version 4.1.2 makes the calendar and notification behavior more predictable. Insights uses a fixed
Monday–Sunday calendar week; Licences & resources has a structured, offline-readable Material layout;
and Android's required passive-capture notification remains visible while tracking without counting
as an unread app-icon notification.

Version 4.1.1 keeps the adaptive capture and client-encrypted recovery introduced in v4.1.0, and
hardens long-running use: Today crosses midnight and time-zone changes without reopening, map
rendering stays memory-bounded on dense history, capture teardown avoids a reverse-geocoding delay,
and complete local erasure cancels pending writes before verifying every auxiliary store was cleared.

Version 4.1.0 made all-day capture adaptive to movement and stillness, added client-encrypted cloud
recovery and explicit data-lifecycle controls, and introduced Personal, Field work and Police diary
templates. Today leads with the route map and capture summary; Days cards keep ordered place details
behind **More**. The Time metric totals measured capture intervals rather than the wall-clock span
from the first fix to the last.

Warm Butter `#EED77B` is consistent across the launcher, route, stops, selected states, identity
accents, and buttons. Neutral Carbon dark-mode layers remain unchanged, with accessible
foreground/background contrast in both themes.

The v4.0.2 hardening remains intact. Names, notes, diary text, places, and cloud settings share
Unicode-safe boundaries in the UI and persistence layers. Long-running actions block duplicate
taps, announce progress and outcomes to assistive technology, and keep recovery close to the
action that failed.

Saved-place cards no longer expose precise coordinates in the ordinary Settings flow. They explain
the practical recognition distance instead, while the cloud provider is chosen before its
provider-specific fields and officer/supervisor information is grouped as Diary identity.

The privacy and reliability guarantees from v3.9.0 remain intact. A place marked **Private zone**
in Settings, and any stop hidden in **Review my day**, are excluded from cloud reports and shared
exports; a private zone is never sent to an automatic address-lookup provider. Online map tiles still
contact the selected tile provider. Reports do not carry raw GPS
coordinates. The System/Light/Dark selector, map-led daily cards, correction/completion review,
private Insights, capture-gap disclosure, and the one-hour privacy pause are unchanged.

Private zones and hidden stops remain in your own cloud backup, so a restored phone still knows
which places are private.

DSR is a separate app in the private `sampathmannam/dsr` repository; its old
DailyBeat records and PDFs are retained, not deleted. Regular QA uses `com.dailybeat.app.qa`.
Destructive instrumentation must use
`com.dailybeat.app.qa.e2eloop`, protecting regular QA and production `com.dailybeat.app` data.

## Local drafting and optional Cloud AI

Deterministic local diary drafts work without an account, API key, or internet connection. They
organize recorded visits and notes into a chronology; they do not run a local generative model.

Cloud AI is optional and off by default. Its default provider preset is DeepSeek (`deepseek-chat`),
with other providers available in Settings → Cloud AI. Cloud drafting requires internet access,
explicit enablement, and a valid provider key. No provider key is included in the source or APK.
Restore preserves the provider/model/address configured on this phone and disables Cloud AI and
automatic evening reports until the user enables them again.

## Cloud backup

Cloud backup uses a DailyBeat Supabase project configured at build time with `SUPABASE_URL` and
`SUPABASE_ANON_KEY`. These are public client configuration; authenticated owner-only row-level
security protects each backup. New backups are encrypted on the phone with a separate recovery
passphrase that is never uploaded or stored in settings. The Cloud AI provider key is never backed up.
The separate F-Droid store candidate excludes the managed-backup configuration.

After installation, open Settings → Cloud backup, sign in, set a recovery passphrase, and select
**Back up now**. On a replacement phone, install the same signed DailyBeat package, sign in to the
same account, enter the recovery passphrase, and explicitly confirm **Restore from cloud**. Restore
replaces local records only after the complete decrypted snapshot validates.

## Signed APK

For the hardening contents and validation evidence, see the
[24 September review](hardening/2026-09-24.md) and [changelog](../CHANGELOG.md).
The verified Mac installer now requires Android SDK build-tools (`apksigner`, `aapt`) and a JDK,
checks the release checksum and permanent signing certificate, and leaves permissions to Android's
normal consent flow. Its default tag follows `release/version.txt`; it does not uninstall or downgrade.

After every release gate passes and publication completes, GitHub Releases tag `v4.3.6` provides:

- `DailyBeat-v4.3.6.apk` — signed universal APK for arm64, armv7, x86, and x86_64; version code 40
- `SHA256SUMS.txt` — checksum for that exact filename

Obtainium users should refresh the regular stable channel and install over their existing app.
The separate `fdroid-v4.3.6` prerelease contains `DailyBeat-FDroid-v4.3.6.apk` and
`FDROID-SHA256SUMS.txt`. It supplies the Google-free reference APK for reproducibility review;
it does not mean F-Droid has accepted or published the app.

The publisher accepts only a `main` commit whose version matches `release/version.txt`. It waits
for standard and Google-free builds, Android instrumentation, a real Supabase backup/restore round
trip, executable database isolation tests, the release-policy suite, and security checks, then
verifies the permanent signing-certificate fingerprint before publishing.
Do not create a tag from a feature branch merely to obtain an APK.

## Test the candidate on a physical Android phone

```bash
git clone https://github.com/sampathmannam/dailybeat.git
cd dailybeat
DAILYBEAT_BRANCH=main \
  ./scripts/mac_phone_e2e.sh YOUR_ADB_SERIAL
```

The script rejects emulators, removes and reinstalls only the disposable QA package, runs the
debug build, JVM tests, lint, and Compose instrumentation, and captures a screenshot, launch
result, device build, installed-package details, PID, and logcat under
`android/app/build/outputs/phone-evidence/`. It fails if Android cannot launch the activity, the
process exits, or the log contains an app crash or ANR.

To include the mandatory live backup/restore check, set these values in the current shell before
running the script and set `DAILYBEAT_REQUIRE_LIVE_BACKUP=1`:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `DAILYBEAT_BACKUP_TEST_EMAIL`
- `DAILYBEAT_BACKUP_TEST_PASSWORD`

Use a dedicated, email-confirmed QA account. The same four names are GitHub Actions secrets used
by CI; credential values are never committed.

## Install on Android

1. Download only the APK attached to the GitHub Release and verify it with `SHA256SUMS.txt`.
2. Open the APK and allow installation from the chosen file app if Android asks.
3. Install over the existing signed DailyBeat app; do not uninstall, because uninstalling deletes
   the local database.
4. Grant location and notification permissions. Microphone access is requested only when recording
   a voice note.
5. Configure Cloud AI and cloud backup in Settings only if desired; core local use requires neither.

## Verify the core offline path

1. Leave Cloud AI unconfigured and generate a local diary from recorded visits or typed notes.
   If testing voice input offline, first ensure the phone's speech engine and selected language
   support offline recognition; a successfully recognized transcript must save locally.
2. Add and then clear an optional diary note; reopen the day and verify it remains cleared.
3. Turn GPS capture on, background the app, move between two places, and verify the Today map and
   Days card update after returning.
4. Verify the four tabs: Today, Days, Insights and Settings. DSR must not appear.
5. Open Review My Day, rename one stop, hide and restore it, name the Beat, and mark it complete.
6. Open the full map and verify a Warm Butter route appears immediately while interactive tiles load;
   when the street map is ready, tap **Replay route** and verify the route draws from start to finish.
7. Export a week package and open the shared ZIP; existing diary text must remain unchanged.
8. Deny map/network access; the diary, route list, notes, and export must remain usable.
9. Confirm that capture gaps do not ask for manual review; uncertain segments stay out of distance totals.
10. In **Settings → Places**, mark a saved place as a **Private zone**, then generate a report for a
    day that includes a stay there. The generated diary must not mention that place. Do the same for
    a stop hidden in **Review my day**. Both must still be visible on your own Today and Days
    screens — they are withheld from the cloud, not deleted.

## Dependency verification

`android/gradle/verification-metadata.xml` pins every Gradle dependency by SHA-256, so a
substituted or typosquatted artifact cannot be linked into a signed release. The policy is
**checksum-only** and `scripts/tests/test_gradle_supply_chain.py` enforces it inside the
`release-policy` gate.

After any dependency or plugin change, regenerate it:

```bash
cd android
./gradlew resolveAapt2Linux assembleDebug assembleDebugAndroidTest testDebugUnitTest \
          lintDebug assembleRelease testReleaseUnitTest \
          --write-verification-metadata sha256 --no-daemon
```

Three things go wrong if this is done casually:

1. **Always regenerate with `sha256` alone.** Adding `,pgp` silently flips `verify-signatures` to
   true, and the build then fails closed on plugin-marker POMs whose keys cannot be fetched from any
   key server. The policy test catches this.
2. **The task list must cover every variant CI builds** — debug, androidTest, unit tests, lint and
   release. Omitting one leaves its artifacts unpinned and that job fails on the runner.
3. **aapt2 is published per platform.** Regenerating on macOS records only the `osx` jar while
   every CI job runs on `ubuntu-latest`. The `resolveAapt2Linux` helper task exists solely to pull
   the linux variant into the same generation pass; the policy test fails if it is missing.

Verify against a cold cache before pushing, because a warm cache always passes:

```bash
cd android
GRADLE_USER_HOME=$(mktemp -d) ./gradlew assembleDebug \
  --refresh-dependencies --dependency-verification strict --no-daemon
```

## Support

Report issues in the `sampathmannam/dailybeat` GitHub repository.
