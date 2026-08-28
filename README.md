# DailyBeat

Local-model-only police dairy writer. Android app, fully offline.

## Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Training data scripts | Done |
| 2–3 | Eval split + `eval_base.py` | Done |
| 6–8 | Android skeleton, LLM, Room events | Done |
| 9 | Voice capture + EventExtractor | Done (Whisper JNI when `ggml-tiny.bin` bundled) |
| 10 | Diary generator on home screen | Done |
| 11 | PDF export + Share | Done |
| 12 | GPS breadcrumbs, call log, Settings | Done |
| 13 | Release APK build | Done |
| 0–5 | Fine-tune on RTX 2050 | Needs your past diaries + local GPU |

## Test on your MacBook emulator (not Cursor cloud)

**This cloud agent cannot run on your Mac.** Clone from GitHub and use a **local** Cursor agent or the scripts below.

```bash
git clone https://github.com/sampathmannam/dailybeat.git ~/github/dailybeat
cd ~/github/dailybeat
./scripts/mac_setup.sh              # one-time
./scripts/mac_sync_and_run.sh       # pull + install on emulator
./scripts/mac_emulator_demo.sh      # demo walkthrough
```

Full guide: [docs/MAC_DEVELOPMENT.md](docs/MAC_DEVELOPMENT.md)

**Local Cursor:** File → Open Folder → dailybeat → Agent with **Local** mode (not Cloud).

## Build (Mac)

```bash
cd android
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed with debug key unless release.keystore + env vars set
```

## Python toolchain

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
pytest scripts/tests/
```

## LLM on device

Copy fine-tuned `dailybeat-q4_k_m.gguf` to Mac **Downloads**, then in app: **Settings → Import model**.

See [PLAN.md](PLAN.md) for the full spec.
