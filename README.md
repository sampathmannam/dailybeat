# DailyBeat

Local-model-only police dairy writer. Android app, fully offline.

## Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Training data scripts + synthetic samples | **Done** |
| 6 | Android project skeleton | **Done** |
| 7 | LLM engine wrapper + generate UI | **Done** (needs GGUF in assets) |
| 8 | Room DB + today's events UI | **Done** |
| 0–5 | Environment, fine-tune | Needs local GPU + past diaries |
| 9–14 | Voice, PDF, release | Planned |

## Quick start

**Training data (Phase 1):**
```bash
python scripts/generate_synthetic_samples.py
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
# → data/diary_train.jsonl (gitignored)
```

**Android (Linux / macOS):**
```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

**Android (Windows PowerShell):**
```powershell
cd android
.\gradlew.bat assembleDebug
```

**Python toolchain:**
```bash
pip install -e ".[dev,train,eval]"
pytest scripts/tests/
```

**LLM on device (Phase 7):** After fine-tune, copy `dailybeat-q4_k_m.gguf` to `android/app/src/main/assets/`.

See [PLAN.md](PLAN.md) for the full end-to-end build spec.
