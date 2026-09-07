# DailyBeat

IPS daily diary app for Android. Passive GPS journey tracking + DeepSeek cloud LLM reports.

**v3.7.0 candidate** — Combines the local-first DSR Command dashboard with the daily journey
feed, map-derived place names, removal of call-log capture, and an end-to-end reliability overhaul.

Diary generation requires Cloud AI, network access, and a valid DeepSeek API key.
Configure it at runtime in Settings → Cloud AI. The key is not bundled in the APK.

## Install

See [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

```bash
# Existing stable release (currently v3.6.0)
./scripts/mac_install_release_apk.sh YOUR_DEVICE_ID

# Build, test, and install the v3.7 QA candidate without replacing the stable app
DAILYBEAT_BRANCH=hardening/end-to-end-reliability \
  ./scripts/mac_phone_e2e.sh YOUR_DEVICE_ID
```

The signed v3.7.0 APK will appear in GitHub Releases only after the hardening branch is merged and
the build, live cloud-backup instrumentation, backend, and security gates all pass. To build the
candidate locally:

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
**Feed → Export week package (ZIP)** writes diaries and PDFs to app storage.

## Quick start (cloud AI)

1. Install the app and complete onboarding.
2. Grant location (including **Allow all the time** on Android 10+) and notifications.
3. **Settings → Cloud AI** — paste your API key, pick provider/model, tap **Test connection**.
4. Keep **GPS tracking** on. Move between places; stays and transit appear on **Today**.
5. Tap **Generate AI daily report** or wait for the 8 PM auto-report.
6. Review, edit, and share PDF from **Diary**, and browse each day on **Feed**.

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
| Named places from OpenStreetMap ("Rasipuram Police Station") | ✅ |
| Cloud LLM reports (OpenAI / Anthropic / compatible) | ✅ |
| Encrypted API key storage | ✅ |
| 8 PM auto evening report | ✅ |
| Optional manual notes | ✅ |
| Offline LLM fallback | ❌ intentionally cloud-only |
| Diary edit + PDF share | ✅ |
| Daily journey feed (route, distance, named stops) | ✅ |
| DSR PDF import + command dashboard | ✅ on-device |
| Duplicate/collision/date/total validation | ✅ |
| All-crime full-year/YTD trend and station gaps | ✅ |
| TASMAC + fatal-accident headline metrics | ✅ |
| Unit tests | ✅ |

## Privacy

- GPS breadcrumbs, visits, and notes stay on your device.
- **Cloud reports** send a text summary of that day's activity to your chosen LLM provider when you generate (or at 8 PM auto-report).
- **Voice notes** use Android's configured speech-recognition service. When Cloud AI is ready, the transcript is sent to the chosen provider for structuring; if that step is unavailable, DailyBeat still saves the transcript locally.
- API keys are stored encrypted on device.
- DSR parsing does not call the configured cloud LLM and DSR records are not included in the cloud backup snapshot.

## Training (your machine)

```bash
pip install -e ".[dev]"
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Full spec: [PLAN.md](PLAN.md) · UI design: [docs/DESIGN.md](docs/DESIGN.md)
