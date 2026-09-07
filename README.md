# DailyBeat

IPS daily diary app for Android. Passive GPS journey tracking + DeepSeek cloud LLM reports.

**v3.6.0** — Adds the local-first DSR Command dashboard and validated operational-PDF imports alongside the existing diary workflow.

Diary generation requires Cloud AI, network access, and a valid DeepSeek API key.
Configure it at runtime in Settings → Cloud AI. The key is not bundled in the APK.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Mac emulator (laptop)
./scripts/mac_setup.sh          # once
./scripts/mac_install_release_apk.sh emulator-5554   # or your device id
```

APKs: GitHub Releases `v3.4.0` or build locally:

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

## DSR Command

1. Open the **DSR** tab and select **Upload DSR or operational PDF**.
2. Choose a Daily DSR, all-crime statement, TASMAC inventory, or fatal-accident PDF.
3. Review the command cards and any verification warnings before relying on the figures.
4. Upload the next DSR the following day. A corrected file for the same report date archives the earlier snapshot instead of double-counting it.

Extraction is performed on the Android device. The dashboard stores operational case metadata, not victim/accused names, phone numbers, addresses, or narrative gists. Source PDFs stay in app-private storage and are excluded from cloud backup. See [docs/DSR_COMMAND.md](docs/DSR_COMMAND.md).

## Features

| Feature | Status |
|---------|--------|
| Passive GPS visits + transit | ✅ |
| Interactive OpenStreetMap journey | ✅ network required |
| OpenStreetMap place names | ✅ |
| Cloud LLM reports (OpenAI / Anthropic / compatible) | ✅ |
| Encrypted API key storage | ✅ |
| 8 PM auto evening report | ✅ |
| Optional manual notes | ✅ |
| Call log capture | ✅ opt-in |
| Offline LLM fallback | ❌ intentionally cloud-only |
| Diary edit + PDF share | ✅ |
| History | ✅ |
| DSR PDF import + command dashboard | ✅ on-device |
| Duplicate/collision/date/total validation | ✅ |
| All-crime full-year/YTD trend and station gaps | ✅ |
| TASMAC + fatal-accident headline metrics | ✅ |
| Unit tests | ✅ |

## Privacy

- GPS breadcrumbs, visits, call log, and notes stay on your device.
- **Cloud reports** send a text summary of that day's activity to your chosen LLM provider when you generate (or at 8 PM auto-report).
- API keys are stored encrypted on device.
- DSR parsing does not call the configured cloud LLM and DSR records are not included in the cloud backup snapshot.

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md)
