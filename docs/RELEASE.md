# DailyBeat v3.3.0 — Install

## Cloud AI configuration

This release uses DeepSeek (`deepseek-chat`) for diary generation. Configure the
provider and API key in Settings → Cloud AI after installation. No API key is
included in the source code or APK. Diary generation requires network access and
a valid DeepSeek key; it does not use an offline fallback.

## APK

Download from GitHub Releases (tag `v2.0.0`):
- `app-release.apk` — recommended for daily phone (~10 MB)
- `app-debug.apk` — development build

If the release has no APK yet, either wait for the **Release APK** GitHub Action on tag `v2.0.0`, or build locally:

```bash
cd android && ./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## Install on Android phone

1. Copy APK to phone (USB, AirDrop, etc.)
2. Open file → Install (allow unknown sources if prompted)
3. Grant permissions when asked: microphone, location, notifications
4. Optional: copy `dailybeat-q4_k_m.gguf` to **Downloads** → Settings → Import model

## Install on emulator (Mac)

```bash
git clone https://github.com/sampathmannam/dailybeat.git
cd dailybeat
./scripts/mac_sync_and_run.sh
```

## Verify offline

1. Enable airplane mode
2. Add manual event → Generate dairy → Share PDF
3. All steps should work without network

## Fine-tune model (your RTX 2050)

1. `python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl`
2. Train per PLAN.md Phase 4–5
3. Copy merged GGUF to phone Downloads → Import in app

## Support

Issues: GitHub Issues on `sampathmannam/dailybeat`
