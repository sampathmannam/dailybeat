# DailyBeat v3.3.0 — Install

## Cloud AI configuration

This release uses DeepSeek (`deepseek-chat`) for diary generation. Configure the
provider and API key in Settings → Cloud AI after installation. No API key is
included in the source code or APK. Diary generation requires network access and
a valid DeepSeek key; it does not use an offline fallback.

## APK

Download from GitHub Releases (tag `v3.3.0`):
- `app-release.apk` — recommended for daily phone (~10 MB)
- `app-debug.apk` — development build

Verify downloads against `SHA256SUMS.txt` in the release assets.

If the release has no APK yet, either wait for the **Release APK** GitHub Action on tag `v3.3.0`, or build locally:

```bash
cd android && ./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## Install on Android phone

1. Copy APK to phone (USB, AirDrop, etc.)
2. Open file → Install (allow unknown sources if prompted)
3. Grant permissions when asked: microphone, location, notifications
4. Open Settings → Cloud AI, enter the DeepSeek API key, and test the connection

## Install on emulator (Mac)

```bash
git clone https://github.com/sampathmannam/dailybeat.git
cd dailybeat
./scripts/mac_sync_and_run.sh
```

## Verify cloud AI

1. Add a manual event
2. Generate the diary using the configured DeepSeek account
3. Review the generated text and share the PDF
4. With networking disabled, generation must report a clear cloud-connection error; no offline model is used

## Support

Issues: GitHub Issues on `sampathmannam/dailybeat`
