# Changelog

## v3.0.0 — 2026-08-28

Cloud AI brain + passive daily capture. GPS journey tracking, OpenStreetMap place names, and end-of-day LLM reports.

### Cloud AI
- OpenAI, Anthropic, and OpenAI-compatible providers (Groq, etc.)
- API key stored with EncryptedSharedPreferences
- Settings: provider chips, model name, base URL, test connection
- Auto-generate report at 8 PM when cloud AI is configured
- Local GGUF model remains fallback when no API key

### Passive capture
- Visit detection: stays (≥8 min / ~150 m) and transit segments
- OpenStreetMap Nominatim reverse geocode with on-device cache (1 req/s)
- Foreground location service with passive tracking notification
- Optional end-of-day typed note (not required for reports)

### UI
- Today tab: journey timeline, GPS/cloud status chips, Generate AI report
- Diary tab: cloud-aware generate button and hints
- Settings: Cloud AI section above capture toggles

### Data
- Room v4: `location_visits`, `geocode_cache` tables
- Visit events logged as `visit` type in event timeline

### Privacy note
Generating cloud reports sends your day's activity summary (places, times, calls, notes) to the LLM provider you choose. Keys and raw GPS stay on device until you generate.

## v2.1.0 — 2026-08-28

Mobbin-inspired UI redesign.

### UI
- Warm canvas background, rounded cards (20dp), metric pills
- Timeline event cards with type color bars and chips
- Extended gold FAB for voice on Today tab
- Grouped settings sections, improved onboarding
- Outlined/filled bottom navigation icons

### MCP
- `.cursor/mcp.json` adds Mobbin server URL alongside Figma

## v2.0.0 — 2026-08-28

Production release. Full offline diary workflow with polished UI, navigation, history, tests, and geofence-aware GPS.

### App experience
- Material 3 theme (navy/gold institutional palette)
- Four-tab navigation: Today | Diary | History | Settings
- First-run onboarding (name + privacy)
- Today dashboard with stats, event delete, voice FAB
- Diary tab: generate from logged events or pasted text, edit, multi-page PDF share
- History: browse and open past diaries by date
- Settings: delete named places, improved layout

### Reliability
- Android SpeechRecognizer fallback when Whisper model not bundled
- GPS breadcrumbs matched to named geofence places
- Multi-page PDF export (no truncation on long dairies)
- Room v3 with indexes + migration from v2
- Removed unused VoiceCaptureService

### Tests
- Unit tests: GeofenceMatcher, DairyFormatter, DayBounds, EventExtractor, PdfExporter, Room repositories
- CI runs `testReleaseUnitTest` on every main push

### Docs
- `docs/DESIGN.md` — UI and navigation spec (Figma unavailable)

## v1.0.1 — 2026-08-28

Reliability and release fixes.

### Fixed
- GitHub Actions release workflow: Android SDK setup (v1.0.0 tag build was stuck)
- LLM load when model was imported from Downloads (not bundled in assets)
- Daily 8 PM reminder rescheduled after device reboot

### Added
- CI workflow: release APK build + Python tests on `main`

## v1.0.0 — 2026-08-28

First reliable walking-skeleton release. Fully offline. No cloud, no telemetry.

See [docs/RELEASE.md](docs/RELEASE.md) for install steps.
