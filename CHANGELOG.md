# Changelog

## 3.4.0 — 2026-08-30

- Replaced the decorative journey sketch with an interactive OpenStreetMap-based map using MapLibre and OpenFreeMap.
- Added chronological route lines, visit markers, automatic journey bounds, attribution controls, an OpenStreetMap full-map action, and a retryable network-failure state.
- Kept one universal APK compatible with arm64, armv7, x86, and x86_64 devices.
- Made synthetic QA seeding idempotent and removed its controls from production builds.
- Hardened encrypted API-key storage, disabled sensitive-data backup, and removed destructive database migration fallback.
- Changed microphone and call-log permissions to request only when their features are used.
- Removed the overlapping voice FAB and dead local-LLM/voice scaffolding; voice capture now reports failures honestly.
- Fixed Today metric sizing, diary grammar, place-coordinate validation, provider-chip layout, stale copy, and Nominatim app identification.
- Expanded unit and Compose instrumentation coverage; verified 10 screen-flow tests, repeated journey flows, lifecycle recovery, and adversarial random input on an Android 14 emulator.

## 3.3.0 — 2026-08-30

- Added DeepSeek as the default OpenAI-compatible cloud provider.
- Made diary generation cloud-only with an explicit configuration/network error.
- Removed the native on-device LLM dependency and moved CI instrumentation to the supported x86_64 emulator.
- Added the Smart Field Note launcher icon with Android themed-icon support.
- Hardened microphone and location capture against permission-revocation races.
- Corrected cloud-only product copy and diary labels across the app and PDF export.
- Stabilized Compose instrumentation selectors and release asset generation.

## v3.2.0 — 2026-08-29

State-of-the-art passive upgrades from v3.2 roadmap.

### Features
- **Voice FAB** on Today — SpeechRecognizer → structured voice events
- **Journey map preview** — offline canvas timeline from GPS visits
- **LLM citations** — [V#]/[E#] refs in context; reports cite sources
- **Weekly AI rollup** on History tab
- **Export week package** — ZIP with diaries, PDFs, audit log
- **Frequent-place learning** — suggests geofences from visit clusters
- **Supervisor name** on PDF sign-off line

## v3.1.0 — 2026-08-28

Triple audit loop: adversarial tests, synthetic data, passive LLM features.

### Features
- Midday cloud pulse (1 PM optional) appended to diary
- Significant moment marker (passive flag, no typing)
- Synthetic demo day generator for QA
- Local capture audit log (transparency)
- LLM report retry worker on network failure
- Context limiter for very long days

### Fixed
- Flush open GPS dwell when location service stops
- PDF export with blank officer/text
- Event input truncation (8k chars)
- OSM geocode invalid coordinate guard
- Diary merge without unsafe null assert

### Testing
- Adversarial unit tests (ContextLimiter, EventRepository)
- Maestro E2E flow (`maestro/onboarding_and_nav.yaml`)
- Honest ratings: `docs/AUDIT_RATING.md`

## v3.0.1 — 2026-08-28

E2E hardening: bug fixes, instrumentation tests, and CI emulator suite.

### Fixed
- BootReceiver restarts passive GPS/call capture after device reboot
- Background location requested after foreground grant (Android 11+ flow)
- GPS status chip reflects permissions, not just the settings toggle
- Optional note section collapses after save
- Settings place validation shows errors instead of silent no-op
- SecureApiKeyStore falls back when encrypted prefs unavailable (tests)
- CallLogWorker and LocationService tolerate restricted/test contexts
- Onboarding copy updated for passive GPS + cloud AI model

### Tests
- Compose instrumentation: onboarding flow, bottom-nav, optional note, settings/diary sections
- Robolectric: BootReceiver smoke test
- CI runs `connectedDebugAndroidTest` on API 34 emulator

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
