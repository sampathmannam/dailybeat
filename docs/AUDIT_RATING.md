# DailyBeat — Honest Audit Ratings (v3.1.0)

Ratings after three audit loops: code review, adversarial unit tests, synthetic data injection, instrumentation test suite, and CI emulator configuration. Scores are **genuine** — not aspirational.

## Scores (out of 10)

| Factor | Score | Notes |
|--------|-------|-------|
| **Passive capture (GPS journey)** | 6.5 | Visit/transit detection works; needs real-world tuning (dwell thresholds, battery). No activity recognition or fused motion sensors. |
| **Cloud LLM integration** | 7.0 | OpenAI/Anthropic/compatible, encrypted key, evening + midday pulse, retry worker. No streaming, no on-device prompt cache, no user-visible token/cost meter. |
| **Reliability / crash resistance** | 7.5 | Fixed reboot capture, permission flow, flush pending dwell, input truncation, PDF fallbacks. Room still uses destructive migration fallback. |
| **E2E test coverage** | 6.0 | Compose instrumentation + Maestro flows + adversarial unit tests + CI emulator job. No screenshot/visual regression; cloud VM adb often offline. |
| **UI / UX polish** | 6.5 | Mobbin-inspired shell; passive-first Today. Voice capture still unwired; no map view; limited empty-state guidance when permissions denied. |
| **Privacy / transparency** | 7.0 | Local audit log, clear cloud-send on generate. No export-audit-to-PDF; no per-field “what leaves device” preview before LLM call. |
| **Offline / local fallback** | 6.0 | GGUF path exists but secondary; passive OSM geocode needs network. No offline place database. |
| **IPS diary output quality** | 7.0 | Depends on cloud model + captured data richness. Synthetic seed helps QA; production quality = f(GPS accuracy, dwell algo). |
| **Production readiness** | 6.5 | Signed release APK, CI, changelog. Missing Play Store hardening, crash reporting (Firebase/Sentry), staged rollout analytics. |

**Overall weighted average: ~6.7 / 10** — credible passive diary prototype with cloud brain, not yet state-of-the-art life-logging.

## Barriers to 10/10

1. **Hardware & OS** — Background location is fragile (OEM kills, permission UX, battery saver). True passive tracking needs foreground service + user trust + possibly companion wearable.
2. **No map / spatial UI** — Officers expect map timeline; we only show text cards.
3. **Voice unwired** — STT exists in codebase but no FAB/wiring; passive promise is incomplete for oral culture workflows.
4. **LLM without guardrails UI** — No citation of source events in generated report, no human-in-the-loop diff view.
5. **Test environment** — Emulator/adb instability in cloud agents; full E2E depends on GitHub Actions emulator or physical device farm (Firebase Test Lab, BrowserStack).
6. **No crash/telemetry loop** — Bugs in the field won’t be visible until users report them.
7. **Geocoding dependency** — Nominatim rate limits and network; no bundled offline geocoder for rural areas.
8. **Single-user local DB** — No backup/sync, no multi-device, no export encryption for sensitive diaries.
9. **Regulatory / institutional** — IPS formats vary by state/cadre; no template picker or supervisor approval workflow.
10. **Activity recognition** — GPS alone cannot distinguish “at court” vs “parked outside court” without Wi‑Fi/cell/beacon fusion.

## Features brainstormed (implemented in v3.1.0)

- ✅ Capture audit log (local transparency)
- ✅ Synthetic demo day generator (QA)
- ✅ Significant moment marker (passive flag, no typing)
- ✅ Midday cloud pulse (1 PM optional)
- ✅ Report retry worker on LLM network failure
- ✅ Context limiter for huge days
- ✅ Visit flush on service stop

## Features recommended for v3.2+

- Map timeline (OSM tiles offline cache)
- Wire voice FAB + Whisper/SpeechRecognizer
- LLM report with inline citations `[visit #3, 10:15]`
- Firebase Test Lab / Maestro in CI on every PR
- Frequent-place auto-learning → geofence suggestions
- Weekly rollup report (cloud LLM)
- Export audit log + diary as encrypted ZIP
- Activity Recognition API fusion (walking/driving/still)
- Supervisor sign-off PDF field
- On-device embedding search over past diaries

## Audit loops completed

| Loop | Focus | Outcome |
|------|-------|---------|
| 1 | Code audit, synthetic data, adversarial unit tests | 8+ fixes, new audit/synthetic modules |
| 2 | Instrumentation expansion, Maestro flows, retry/pulse | CI emulator job, maestro YAML |
| 3 | Passive features + ratings doc | v3.1.0 release, this document |
