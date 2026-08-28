# DailyBeat v1.0.0 — Install

## APK

Download from GitHub Releases:
- `app-release.apk` — recommended for daily phone
- `app-debug.apk` — development build

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
