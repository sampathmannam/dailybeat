# DailyBeat

Local-model-only police dairy writer. Android app, fully offline.

## Status

| Phase | Description | Status |
|-------|-------------|--------|
| 0–5 | Environment, training data, fine-tune | Not started (needs local GPU + past diaries) |
| 6 | Android project skeleton | **Done** — builds `app-debug.apk` |
| 7–14 | LLM, Room, voice, PDF, release | Planned |

## Quick start

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
```

See [PLAN.md](PLAN.md) for the full end-to-end build spec.
