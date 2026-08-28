# DailyBeat

Local-model-only police dairy writer. Android app, fully offline.

**v1.0.0** — walking-skeleton release. Reliable core path: events → dairy → PDF. Works without fine-tuned model (rule-based fallback).

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Mac emulator
./scripts/mac_sync_and_run.sh
```

APKs: GitHub Releases `v1.0.0` or build locally:

```bash
cd android && ./gradlew assembleRelease
```

## Features

| Feature | Status |
|---------|--------|
| Manual + voice events | ✅ |
| Today's dairy generation | ✅ (LLM or fallback) |
| PDF share | ✅ |
| GPS breadcrumbs | ✅ opt-in |
| Call log | ✅ opt-in |
| Fine-tuned GGUF | Import from Downloads |
| Whisper STT | Fallback until `ggml-tiny.bin` bundled |

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md)
