# Changelog

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
