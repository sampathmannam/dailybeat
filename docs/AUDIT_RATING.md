# DailyBeat — Honest Release-Candidate Rating (v3.7.0)

This assessment covers `hardening/end-to-end-reliability`. It reflects source review, the JVM and
Android suites on an API 34 emulator, 14 Python policy/tooling tests, Android Lint, APK assembly,
adversarial GPS/cloud/backup cases, CI failure-evidence capture, and a successful Android 17
physical-phone gate. CI now requires the live cloud-backup round trip instead of accepting a skip.

It is not a claim that DailyBeat is bug-free or field-proven. The isolated QA package passed the
connected-phone gate, but a multi-day physical-device trial is still required before calling v3.7
field-proven.

## Scores (out of 10)

| Factor | Score | Evidence and remaining risk |
|--------|-------|-----------------------------|
| Passive GPS capture | 8.2 | Checkpointed visit state, sticky foreground-service recovery, every batched fix consumed, cross-midnight clipping, timestamp/coordinate rejection, and sparse/overlap tests. OEM task killers, patrol accuracy, and battery drain still need multi-day measurement. |
| Cloud LLM integration | 8.4 | Encrypted runtime key, bounded requests/responses, explicit token budgets, typed transient retries, citation validation with one correction attempt, and fail-closed report saving. Real-provider latency, cost, and contract drift are outside CI. |
| Reliability / crash resistance | 8.7 | Async failures are surfaced, rapid repeated actions are guarded, logs/files are bounded, database restore validates before its transaction, and build/unit/lint gates are mandatory. Privacy-preserving field crash telemetry is not yet present. |
| End-to-end coverage | 8.5 | API 34 navigation, onboarding, notes, feed, full-map navigation, lifecycle recovery, capture-off behavior, and backup-unconfigured UI are automated. The live backup contract and physical-device matrix remain separate gates. |
| UI / UX | 8.3 | Dedicated full-screen map, lightweight feed/Today routes, lifecycle-refreshed status, explicit loading/error states, safe sharing, and draft recovery. Automated accessibility and multi-size screenshot comparison remain outstanding. |
| Privacy / security | 8.5 | Call-log capture is removed, API keys use encrypted storage with no plaintext fallback, cloud inputs are bounded/sanitized, provider endpoints are restricted, and restore is fail-closed. Exports are intentionally readable files and still require an operational retention policy. |
| Network resilience | 8.2 | Cloud work waits for connectivity, retries only transient failures with bounded backoff, geocoding/map failures degrade without blocking local capture, and offline voice transcripts still save. Map/geocoder providers have no app-owned SLA. |
| Diary output quality | 8.3 | Generated blocks preserve officer text, citations must match visible source references, long context/output is bounded, pending edits flush before generation, and PDF/ZIP edge cases are tested. Operational format and factual usefulness still need officer review. |
| Release engineering | 8.8 | Pinned actions, wrapper validation, parallel build/instrumentation gates, mandatory live backup, permanent-certificate verification, main/version checks, versioned APK, checksum, timeouts, and uploaded evidence. Staged rollout and automated rollback are not implemented. |

**Overall: 8.5 / 10.** This is a substantially hardened release candidate. A score above 9 would
be misleading until the physical-phone, real-patrol, and live-provider evidence below exists.

## What blocks field-production confidence

1. Complete the credential-gated live Supabase backup/restore round trip and retain its CI result.
2. Complete a 72-hour foreground/background/locked-screen trial with battery optimization both
   enabled and exempted; measure missed visits, false visits, GPS error, and battery drain.
3. Exercise DeepSeek (and every enabled provider) with restricted test keys across success, rate
   limit, outage, malformed response, and network-loss cases; record latency and cost.
4. Verify a restore onto a second physical device using the same non-production backup account.
5. Add automated accessibility checks and screenshot comparisons for small, large-font, and
   landscape phone configurations.
6. Complete a formal threat/privacy review covering cloud payloads, readable exports, retention,
   device loss, and incident response.
7. Measure OpenFreeMap/Nominatim availability and define a service or self-hosted fallback plan if
   usage grows.
8. Evaluate diary accuracy, citation usefulness, editing time, and institutional-format compliance
   with real but appropriately protected operational samples.
9. Add privacy-preserving crash/ANR monitoring plus documented support and rollback ownership.
10. Use a staged rollout before promoting v3.7.0 to every device.

## Verification loops

| Loop | Scope | Gate |
|------|-------|------|
| Source hardening | Capture, diary, cloud, backup, export, map, release, privacy | Review plus focused regression tests |
| Deterministic verification | APK assembly, JVM tests, Android Lint, 14 Python tests | GitHub `build` and `patrolgrid-backend` jobs |
| Device-shaped verification | 16 API 34 instrumentation methods, lifecycle and UI flows, screenshot/logcat evidence | GitHub `instrumentation` job |
| Physical verification | Same gates on isolated `com.dailybeat.app.qa`, then launch/PID/crash/ANR check | Connected-phone script; required before release promotion |
