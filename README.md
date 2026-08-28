# DailyBeat

Local-model-only police dairy writer. Android app, fully offline.

**v2.0.0** — production release. Today → Diary → PDF → History. Geofence GPS, speech fallback, unit tests.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Mac emulator (laptop)
./scripts/mac_setup.sh          # once
./scripts/mac_sync_and_run.sh   # each session — needs emulator or phone via adb
```

APKs: GitHub Releases `v2.0.0` or build locally:

```bash
cd android && ./gradlew assembleRelease
```

## Features

| Feature | Status |
|---------|--------|
| Today dashboard + events | ✅ |
| Diary generate / edit / PDF | ✅ |
| History (past days) | ✅ |
| Manual + voice events | ✅ |
| SpeechRecognizer STT fallback | ✅ |
| LLM dairy (GGUF import) | ✅ |
| Rule-based fallback | ✅ |
| GPS + geofence places | ✅ |
| Call log | ✅ opt-in |
| Onboarding | ✅ |
| Unit tests | ✅ |

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md)
