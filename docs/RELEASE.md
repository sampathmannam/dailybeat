# DailyBeat v3.8.0 — install and verify

Version 3.8.0 turns passive location capture into a trustworthy whole-day Beat: map-first Today,
map-led daily cards, correction/completion review, private Insights, capture-gap disclosure, and a
one-hour privacy pause. DSR is a separate app in the private `sampathmannam/dsr` repository; its old
DailyBeat records and PDFs are retained, not deleted. Regular QA uses `com.dailybeat.app.qa`.
Destructive instrumentation must use
`com.dailybeat.app.qa.e2eloop`, protecting regular QA and production `com.dailybeat.app` data.

## Cloud AI configuration

This release defaults to DeepSeek (`deepseek-chat`) and supports the other cloud-provider options
shown in Settings. Configure the provider and API key in Settings → Cloud AI after installation.
No provider key is included in the source or APK. Diary generation requires network access and a
valid key; it does not use an offline model fallback.

## Cloud backup

Cloud backup uses a DailyBeat Supabase project configured at build time with `SUPABASE_URL` and
`SUPABASE_ANON_KEY`. These are public client configuration; authenticated owner-only row-level
security protects each backup. The Cloud AI provider key is never backed up.

After installation, open Settings → Cloud backup, sign in, and select **Back up now**. On a
replacement phone, install the same signed DailyBeat package, sign in to the same account, and
explicitly confirm **Restore from cloud**. Restore replaces local records only after the complete
remote snapshot validates.

## Signed APK

After every release gate passes, download these assets from GitHub Releases (tag `v3.8.0`):

- `DailyBeat-v3.8.0.apk` — signed universal APK for arm64, armv7, x86, and x86_64
- `SHA256SUMS.txt` — checksum for that exact filename

The publisher accepts only a `main` commit whose version matches `release/version.txt`. It waits
for build, Android instrumentation (including a real Supabase backup/restore round trip), backend,
and CodeQL checks, then verifies the permanent signing-certificate fingerprint before publishing.
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
5. Configure Cloud AI and cloud backup in Settings.

## Verify the core offline path

1. Leave Cloud AI unconfigured and record a voice note; the recognized transcript must still save
   locally.
2. Add and then clear an optional diary note; reopen the day and verify it remains cleared.
3. Turn GPS capture on, background the app, move between two places, and verify the Today map and
   Days card update after returning.
4. Verify the four tabs: Today, Days, Insights and Settings. DSR must not appear.
5. Open Review My Day, rename one stop, hide and restore it, name the Beat, and mark it complete.
6. Open the full map and verify a yellow route appears immediately while interactive tiles load.
7. Export a week package and open the shared ZIP; existing diary text must remain unchanged.
8. Deny map/network access; the diary, route list, notes, and export must remain usable.

## Support

Report issues in the `sampathmannam/dailybeat` GitHub repository.
