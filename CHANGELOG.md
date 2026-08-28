# Changelog

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

### Core flow (works without fine-tuned model)
- Log manual events and voice notes (regex / demo transcript on emulator)
- Generate today's formal dairy (LLM when GGUF present, rule-based fallback otherwise)
- Edit and persist dairy per day in local Room DB
- Share dairy as PDF

### Capture
- Optional GPS breadcrumbs (foreground service, permission-gated)
- Optional call log polling (opt-in, deduplicated)
- Mic FAB for voice events with structured extraction

### Settings
- Officer name for PDF header
- Import `dailybeat-q4_k_m.gguf` from Downloads
- Named places (geofence list for future use)
- Daily 8 PM reminder notification

### Build
- Debug and release APKs
- Min SDK 26, target SDK 34

### Not in this APK (by design)
- Fine-tuned GGUF model (copy to Downloads after Phase 5 training)
- Whisper `ggml-tiny.bin` native STT (voice uses fallback until bundled)
- Play Store distribution

See [docs/RELEASE.md](docs/RELEASE.md) for install steps.
