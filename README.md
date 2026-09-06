# DailyBeat

IPS daily diary app for Android. Passive GPS journey tracking + DeepSeek cloud LLM reports.

**v3.6.0** — Daily journey feed, place names taken from the map, call-log capture removed, and a capture/reliability overhaul.

Diary generation requires Cloud AI, network access, and a valid DeepSeek API key.
Configure it at runtime in Settings → Cloud AI. The key is not bundled in the APK.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Mac emulator (laptop)
./scripts/mac_setup.sh          # once
./scripts/mac_install_release_apk.sh emulator-5554   # or your device id
```

APKs: GitHub Releases `v3.6.0` or build locally:

```bash
cd android && ./gradlew assembleRelease
```

## Installing on your phone, and updating without losing data

Use [Obtainium](https://github.com/ImranR98/Obtainium) and point it at
`https://github.com/sampathmannam/dailybeat`. It tracks GitHub Releases and installs each new
APK over the existing one, so the database, settings, and API key are all kept.

Two rules keep updates working:

1. **Only install APKs from GitHub Releases.** Every release is signed in CI with one permanent
   key, and the workflow refuses to publish unless the certificate fingerprint matches
   `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`. An APK you build locally
   is signed with a different key, and Android will refuse to install it over the released app.
   Recovering from that means uninstalling, which deletes the diary.
2. **Never uninstall to "fix" an update.** Uninstalling wipes the database. If an update refuses
   to install, it is a signing mismatch (rule 1), not something an uninstall should solve.

Data is carried across updates by Room migrations. `MigrationChainTest` fails the build if the
schema version is ever raised without a migration for that step, and `MigrationTest` exercises
each shipped upgrade path against real SQLite. `fallbackToDestructiveMigration` is not used and
`DatabasePolicyTest` keeps it that way.

Before a risky update you can also take a copy from **Settings → Cloud backup**, and
**Feed → Export week package (ZIP)** writes diaries and PDFs to app storage.

## Quick start (cloud AI)

1. Install the app and complete onboarding.
2. Grant location (including **Allow all the time** on Android 10+) and notifications.
3. **Settings → Cloud AI** — paste your API key, pick provider/model, tap **Test connection**.
4. Keep **GPS tracking** on. Move between places; stays and transit appear on **Today**.
5. Tap **Generate AI daily report** or wait for the 8 PM auto-report.
6. Review, edit, and share PDF from **Diary**, and browse each day on **Feed**.

## Features

| Feature | Status |
|---------|--------|
| Passive GPS visits + transit | ✅ |
| Interactive OpenStreetMap journey | ✅ network required |
| Named places from OpenStreetMap ("Rasipuram Police Station") | ✅ |
| Cloud LLM reports (OpenAI / Anthropic / compatible) | ✅ |
| Encrypted API key storage | ✅ |
| 8 PM auto evening report | ✅ |
| Optional manual notes | ✅ |
| Offline LLM fallback | ❌ intentionally cloud-only |
| Diary edit + PDF share | ✅ |
| Daily journey feed (route, distance, named stops) | ✅ |
| Unit tests | ✅ |

## Privacy

- GPS breadcrumbs, visits, and notes stay on your device.
- **Cloud reports** send a text summary of that day's activity to your chosen LLM provider when you generate (or at 8 PM auto-report).
- API keys are stored encrypted on device.

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md)
