# DailyBeat v3.6.0 release candidate — install and verify

## Cloud AI configuration

This release defaults to DeepSeek (`deepseek-chat`) and also supports the app's
other cloud-provider options. Configure the provider and API key in Settings →
Cloud AI after installation. No API key is included in the source code or APK.
Diary generation requires network access and a valid provider key; it does not
use an offline fallback.

## Cloud backup

Cloud backup uses a DailyBeat Supabase project configured at build time with
`SUPABASE_URL` and `SUPABASE_ANON_KEY`. These are public client configuration;
the database protects every backup with authenticated owner-only row-level
security. The release workflow reads both values from GitHub Actions secrets.

After installation, open Settings → Cloud backup, sign in, and select **Back up
now**. On a replacement phone, install the same signed DailyBeat package, sign
in to the same account, and explicitly confirm **Restore from cloud**. Restore
replaces local records only after the complete remote snapshot validates.

The Cloud AI provider key is never backed up. Enter it again on a new phone.

## Signed APK

After the hardening branch is merged and the tag is published, download from GitHub Releases
(tag `v3.6.0`):

- `DailyBeat-v3.6.0.apk` — signed universal APK for arm64, armv7, x86, and x86_64
- `SHA256SUMS.txt` — checksum for that exact filename

Verify downloads against `SHA256SUMS.txt` in the release assets.

The release workflow refuses tags that are not on `main`, whose version does not match the tag,
or whose `verify` and `instrumentation` checks have not passed. Do not tag a branch merely to
obtain an APK.

## Test the release candidate on an Android phone

The QA application ID is `com.dailybeat.app.qa`, so this procedure does not overwrite the signed
stable app or its data:

```bash
git clone https://github.com/sampathmannam/dailybeat.git
cd dailybeat
DAILYBEAT_BRANCH=hardening/end-to-end-reliability \
  ./scripts/mac_phone_e2e.sh YOUR_ADB_SERIAL
```

The script runs the debug build, JVM tests, lint, and Compose instrumentation on the connected
phone, then launches the QA app and writes a screenshot, launch result, PID, and logcat under
`android/app/build/outputs/phone-evidence/`.

## Install on Android phone

1. Copy APK to phone (USB, AirDrop, etc.)
2. Open file → Install (allow unknown sources if prompted)
3. Grant permissions when asked: location and notifications; microphone is requested only when
   recording a voice note
4. Open Settings → Cloud AI, enter the DeepSeek API key, and test the connection

## Build and install QA on an emulator (Mac)

```bash
git clone https://github.com/sampathmannam/dailybeat.git
cd dailybeat
DAILYBEAT_BRANCH=hardening/end-to-end-reliability ./scripts/mac_sync_and_run.sh
```

## Verify cloud AI

1. Add a manual event
2. Generate the diary using the configured DeepSeek account
3. Review the generated text and share the PDF
4. With networking disabled, generation must report a clear cloud-connection error; no offline model is used

## Verify the core offline path

1. Leave Cloud AI unconfigured and record a voice note; the recognized transcript must still save locally.
2. Add and then clear an optional diary note; reopen the day and verify it remains cleared.
3. Turn GPS capture on, background the app, move between two places, and verify the Today count
   and History feed update after returning.
4. Export a week package and open the shared ZIP; existing diary text must remain unchanged.
5. Deny map/network access; the diary, route list, notes, and export must remain usable.

## Support

Issues: GitHub Issues on `sampathmannam/dailybeat`
