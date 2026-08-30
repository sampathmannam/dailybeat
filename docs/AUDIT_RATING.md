# DailyBeat — Honest Release-Candidate Ratings (v3.4.0)

These scores reflect verified local and emulator evidence, not a claim that the app is bug-free. The audit included unit tests, 10 Compose instrumentation flows, synthetic data, permission denial, lifecycle/process recovery, map/network behavior, lint, APK ABI inspection, and a 1,000-event random-input pass.

## Scores (out of 10)

| Factor | Score | Evidence and remaining risk |
|--------|-------|-----------------------------|
| Passive GPS capture | 7.0 | Visit/transit tracking and foreground-service recovery work in tests. Battery use, OEM task killers, and dwell thresholds still need multi-day field validation. |
| Cloud LLM integration | 7.5 | Encrypted runtime key, multiple providers, connection test, retries, scheduled reports, and no false offline fallback. Real-provider reliability and cost are not covered by CI. |
| Reliability / crash resistance | 8.0 | Safe migrations, sensitive backup disabled, permission races addressed, 10/10 UI tests, lifecycle recovery, and 1,000 random events without a DailyBeat crash/ANR. No field telemetry yet. |
| E2E test coverage | 7.5 | Android 14 instrumentation, synthetic data, unit/adversarial tests, and CI emulator wiring. No physical-device farm, screenshot regression gate, or live cloud contract test. |
| UI / UX | 7.8 | Passive-first Today, real journey map, consistent navigation, visible empty/error states, and corrected overlap/alignment issues. Accessibility and small-screen coverage need expansion. |
| Privacy / security | 8.0 | API key is encrypted, plaintext fallback removed, backups disabled, and cloud-only data flow is disclosed. A formal threat model and encrypted exports remain outstanding. |
| Network resilience | 6.8 | Map failure is isolated with retry; geocoding and cloud failures degrade honestly. OpenFreeMap and Nominatim are external best-effort services without an app-owned SLA. |
| Diary output quality | 7.2 | Source context, citations, editing, PDF, and weekly output are present. Accuracy still depends on GPS quality, provider model, and real operational evaluation. |
| Release engineering | 8.0 | Permanent signing certificate, checksum, one universal APK, CI, versioned workflow, and Obtainium-compatible release shape. No staged rollout or Play pre-launch report. |

**Overall: 7.6 / 10.** This is a credible, deployable release candidate, not a 10/10 field-proven product.

## What blocks 10/10

1. Multi-day physical-device trials across Motorola/Samsung/Pixel with battery optimization enabled.
2. Measured GPS accuracy, missed-visit rate, false-visit rate, and battery drain in real patrol movement.
3. Live cloud-provider contract tests using a restricted test account, plus latency/cost/error dashboards.
4. Privacy-preserving crash reporting and an operational incident/rollback process.
5. A physical-device test matrix, accessibility automation, and visual regression checks for multiple screen sizes.
6. Formal security/privacy review for diary exports, call-log capture, cloud payloads, and retention.
7. A map/geocoding service strategy with an SLA or self-hosted fallback if usage grows.
8. User evaluation of generated diary accuracy, citations, editing time, and institutional format compliance.
9. Current Play target/API compliance and a staged store rollout if Play distribution is pursued.
10. Documented support ownership, monitoring, and recovery procedures after release.

## Verified audit loops

| Loop | Focus | Result |
|------|-------|--------|
| 1 | Source, dead wiring, security, and validation | Removed unsafe fallbacks/dead code; fixed permissions, validation, copy, and data safety. |
| 2 | Synthetic and screen-wise emulator testing | 10 Compose flows passed; synthetic seeding became repeatable; UI failures were reproduced and fixed. |
| 3 | Real map, lifecycle, ABI, and adversarial behavior | OSM map rendered with route/markers; all four ABIs packaged; lifecycle recovery and 1,000 random events produced no app crash/ANR. |
