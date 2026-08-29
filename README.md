# DailyBeat

IPS daily diary app for Android. Passive GPS journey tracking + cloud LLM reports (or offline GGUF fallback).

**v3.2.0** — Voice FAB, journey map, LLM citations, weekly rollup, place learning, ZIP export.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Mac emulator (laptop)
./scripts/mac_setup.sh          # once
./scripts/mac_install_release_apk.sh emulator-5554   # or your device id
```

APKs: GitHub Releases `v3.1.0` or build locally:

```bash
cd android && ./gradlew assembleRelease
```

## Quick start (cloud AI)

1. Install the app and complete onboarding.
2. Grant location (including **Allow all the time** on Android 10+) and notifications.
3. **Settings → Cloud AI** — paste your API key, pick provider/model, tap **Test connection**.
4. Keep **GPS tracking** on. Move between places; stays and transit appear on **Today**.
5. Tap **Generate AI daily report** or wait for the 8 PM auto-report.
6. Review, edit, and share PDF from **Diary** or **History**.

## Features

| Feature | Status |
|---------|--------|
| Passive GPS visits + transit | ✅ |
| OpenStreetMap place names | ✅ |
| Cloud LLM reports (OpenAI / Anthropic / compatible) | ✅ |
| Encrypted API key storage | ✅ |
| 8 PM auto evening report | ✅ |
| Optional manual notes | ✅ |
| Call log capture | ✅ opt-in |
| Local GGUF fallback | ✅ |
| Diary edit + PDF share | ✅ |
| History | ✅ |
| Unit tests | ✅ |

## Privacy

- GPS breadcrumbs, visits, call log, and notes stay on your device.
- **Cloud reports** send a text summary of that day's activity to your chosen LLM provider when you generate (or at 8 PM auto-report).
- API keys are stored encrypted on device.

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md)
