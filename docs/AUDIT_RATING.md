# DailyBeat — Honest Release-Candidate Ratings (v3.5.0)

These scores reflect verified local, emulator, and bounded Motorola evidence, not a claim that the app is bug-free. The audit includes unit tests, Compose instrumentation, synthetic data, permission denial, lifecycle/process recovery, map/network behavior, lint, APK ABI inspection, a 1,000-event random-input pass, and a deterministic localhost load gate. Three complete Motorola loops executed; loops 2 and 3 failed the retained-memory gate.

## Scores (out of 10)

| Factor | Score | Evidence and remaining risk |
|--------|-------|-----------------------------|
| Passive GPS capture | 7.0 | Visit/transit tracking and foreground-service recovery work in tests. Battery use, OEM task killers, and dwell thresholds still need multi-day field validation. |
| Cloud LLM integration | 7.5 | Encrypted runtime key handling, bounded requests, retry classification, and citation validation are covered locally. The final live contract call was withheld because the three-loop prerequisite failed, so provider reliability and cost remain unverified. |
| Reliability / crash resistance | 8.0 | Safe migrations, sensitive backup disabled, permission races addressed, 10/10 UI tests, lifecycle recovery, and 1,000 random events without a DailyBeat crash/ANR. No field telemetry yet. |
| E2E test coverage | 7.4 | Android 14 instrumentation, synthetic data, unit/adversarial tests, and CI emulator wiring are present. Three Motorola loops completed every critical-path action, but only loop 1 passed because loops 2 and 3 retained monotonically increasing PSS after map closes. |
| UI / UX | 7.8 | Passive-first Today, real journey map, consistent navigation, visible empty/error states, and corrected overlap/alignment issues. Accessibility and small-screen coverage need expansion. |
| Privacy / security | 8.0 | API key is encrypted, plaintext fallback removed, backups disabled, and cloud-only data flow is disclosed. A formal threat model and encrypted exports remain outstanding. |
| Network resilience | 6.8 | Map failure is isolated with retry; geocoding and cloud failures degrade honestly. OpenFreeMap and Nominatim are external best-effort services without an app-owned SLA. |
| Diary output quality | 7.2 | Source context, citations, editing, PDF, and weekly output are present. Accuracy still depends on GPS quality, provider model, and real operational evaluation. |
| Release engineering | 8.1 | Permanent signing certificate, checksum, one universal APK, CI, a nondestructive rollback runbook, and a localhost performance budget are present. The failed Motorola gate prevents promotion. |

**Overall: 7.6 / 10.** This remains a credible release candidate, but it is a no-go for promotion until the repeated map-memory signal is diagnosed and three clean physical-device loops pass.

## Fixed code and pipeline blockers

- Report persistence is gated by exact citation validation, with one bounded correction attempt.
- Cloud token budgets and retry classification are explicit and locally tested.
- MapLibre is deferred to the dedicated full-map destination instead of normal navigation.
- Required CI separates deterministic checks from emulator coverage and retains failure evidence.
- The synthetic mock covers valid, invalid-citation, empty, 429, and 500 contracts without an external key.
- The bounded localhost profile passed 5,490 requests with zero unexpected failures, 5 ms p95, and 7 ms p99.

## Residual operational gaps

- **Multi-day battery/GPS:** no multi-day field measurement yet covers battery drain, OEM task killing, missed visits, false visits, or dwell thresholds.
- **Device matrix:** one Motorola is not a Samsung/Pixel/API-level matrix. On that Motorola, loops 2 and 3 repeatedly failed monotonic post-map PSS growth despite clean crash/ANR checks.
- **External-service SLA:** DeepSeek, OpenFreeMap, Nominatim, and Supabase remain external dependencies without an app-owned availability or latency SLA.
- **Remote telemetry:** there is no privacy-preserving remote crash, ANR, or release-health signal; current evidence is local and manually collected.

## What blocks 10/10

1. Multi-day physical-device trials across Motorola/Samsung/Pixel with battery optimization enabled.
2. Measured GPS accuracy, missed-visit rate, false-visit rate, and battery drain in real patrol movement.
3. A separately authorized capped cloud-provider contract test using a restricted test account, plus latency/cost/error dashboards.
4. Privacy-preserving crash reporting; rollback is now documented, but remote incident detection is not.
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

## Task 7 Motorola evidence

All loops completed onboarding, synthetic 7/8 then 0/0 loading, all four tabs,
three fully rendered dedicated-map open/close cycles, landscape/portrait,
Home/resume, live PID, and zero recent QA crash/ANR checks.

| Loop | Post-close PSS (KB) | Result |
|------|----------------------|--------|
| 1 | 262,902 → 266,631 → 266,604 | PASS — not monotonic |
| 2 | 257,272 → 259,994 → 261,269 | FAIL — monotonic growth |
| 3 | 255,475 → 258,805 → 259,385 | FAIL — monotonic growth |

The final live DeepSeek gate was not run because its prerequisite—all three
Motorola loops passing—was false. No provider request was issued.
