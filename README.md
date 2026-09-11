# DailyBeat

IPS daily diary app for Android. Passive GPS journey tracking + DeepSeek cloud LLM reports.

**v3.8.3** — A private whole-day activity record with enforced private zones, a persistent
System/Light/Dark appearance selector, map-first Today, reviewable daily Beats, actionable 28-day
insights, capture-health controls, and stronger GPS reliability.
DSR remains a separate app.

Diary generation requires Cloud AI, network access, and a valid DeepSeek API key.
Configure it at runtime in Settings → Cloud AI. The key is not bundled in the APK.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Current stable release (v3.8.3)
./scripts/mac_install_release_apk.sh YOUR_DEVICE_ID

# Build, test, and install a QA candidate without replacing the stable app
DAILYBEAT_BRANCH=main \
  ./scripts/mac_phone_e2e.sh YOUR_DEVICE_ID
```

The signed v3.8.3 APK is published through GitHub Releases only after the build, live cloud-backup
instrumentation, release-policy, and security gates all pass. To build an isolated QA APK locally:

```bash
cd android && ./gradlew assembleDebug
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
**Days → Weekly tools → Export week package (ZIP)** writes diaries and PDFs to app storage.

## Quick start (cloud AI)

1. Install the app and complete onboarding.
2. Grant location (including **Allow all the time** on Android 10+) and notifications.
3. **Settings → Cloud AI** — paste your API key, pick provider/model, tap **Test connection**.
4. Keep **GPS breadcrumbs** on. Move between places; the route and stays appear on **Today**.
5. Optionally open **Review my day** to name the Beat or correct a stop; capture gaps require no action.
6. Add or open the diary from Today/Review, and use **Insights** for the next useful follow-up.

## DSR is a separate app

DSR lives in the private [sampathmannam/dsr](https://github.com/sampathmannam/dsr) repository.
Its screens, importer and PDFBox dependency have been removed from DailyBeat. Legacy DSR tables
and private source files remain untouched for data retention; see [separation notes](docs/DSR_COMMAND.md).

## Features

| Feature | Status |
|---------|--------|
| Passive GPS visits + transit | ✅ |
| Reliable breadcrumb filtering + capture-gap detection | ✅ |
| Interactive OpenStreetMap journey | ✅ network required |
| Immediate route fallback while the interactive map loads | ✅ |
| Animated full-map route replay | ✅ |
| Named places from OpenStreetMap ("Rasipuram Police Station") | ✅ |
| Reviewable daily Beats (rename/hide/restore/complete) | ✅ |
| Private 28-day insights and review streak | ✅ |
| One-hour privacy pause with automatic resume | ✅ |
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
- **Cloud reports** send a text summary of that day's activity to your chosen LLM provider when you generate (or at 8 PM auto-report). The summary carries times, durations and place names — **never raw coordinates**.
- **Private zones** are enforced, not cosmetic. Mark a saved place private in Settings and any stay inside it is excluded from cloud reports and shared exports, and is never sent to the OpenStreetMap geocoder either — so its address is not looked up at all. The same applies to any stop you hide in **Review my day**.
- Private zones stay in your own cloud backup so a restored phone still knows which places are private. Excluding them would silently lose the flag and re-expose those places after a restore.
- **Voice notes** use Android's configured speech-recognition service. When Cloud AI is ready, the transcript is sent to the chosen provider for structuring; if that step is unavailable, DailyBeat still saves the transcript locally.
- API keys are stored encrypted on device.
- Retained legacy DSR records are not included in the cloud backup snapshot.

## Release policy checks

The release gate runs a Python suite that pins the build, signing, QA-isolation and cloud-backup
policies. Run it the same way CI does:

```bash
pip install -e ".[dev]"
python3 -m pytest scripts/tests/ -q
```

`scripts/parse_diaries.py` and `scripts/split_eval.py` are retained as fixtures for that suite.
DailyBeat generates diaries through the cloud provider only; there is no local training pipeline.

Product doctrine: [PRODUCT.md](PRODUCT.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md) · Baseline: [docs/AUDIT_RATING.md](docs/AUDIT_RATING.md)
