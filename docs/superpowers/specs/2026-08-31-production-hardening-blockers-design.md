# DailyBeat Production-Hardening Blockers Design

**Date:** 2026-08-31  
**Status:** Approved design  
**Target:** Next stable GitHub APK release after v3.5.0

## Context

DailyBeat is a deployable release candidate, but current evidence does not support calling it fully production-ready. The latest live DeepSeek test authenticated successfully and saved a diary, but the model cited nonexistent `[V1][V2]` visits when the source day contained no visits. The Android CI build gate is healthy, while the emulator suite has a known slow/flaky interaction between Compose idling and an inline MapLibre view. Device measurements also show that loading MapLibre materially increases process memory. There is no app-owned remote telemetry or staged store rollout, and multi-day GPS/battery validation cannot be manufactured in an overnight code change.

This design fixes every currently identified code-addressable blocker while preserving an honest boundary around field validation and third-party service risk.

## Goals

1. Never silently save a daily report containing impossible source citations.
2. Bound DeepSeek cost and prevent recursive or billable retry loops.
3. Keep the normal Today experience fast and avoid loading MapLibre until the user opens the map.
4. Make required CI deterministic and retain useful evidence when emulator tests fail.
5. Re-test critical paths on the connected Motorola without touching production data or exposing the API key.
6. Publish one signed, Obtainium-compatible stable APK only after all release gates pass.

## Non-goals and honest residual risks

- This release will not claim multi-day battery, missed-visit, or false-visit validation across Motorola, Samsung, and Pixel hardware.
- It will not add third-party analytics or crash reporting without a separate privacy decision and service configuration.
- It cannot create an SLA for DeepSeek, OpenFreeMap, Nominatim, GitHub, or Supabase.
- It will not replace the current direct-to-provider architecture with an app-owned backend.
- It will not claim a bug-free or 10/10 product. Remaining field risks will be documented in the release notes and audit rating.

## Approaches considered

### A. Focused production hardening — selected

Add deterministic AI-output validation and cost caps, separate the lightweight journey preview from the full MapLibre screen, stabilize CI around deterministic checks, add privacy-safe local failure evidence, and run one final capped live-provider test. This addresses the observed blockers without introducing a new production service.

### B. Fast patch only

Fix the citation bug and the current CI test. This is faster, but leaves map startup memory, retry behavior, failure evidence, and release confidence materially weaker.

### C. Platform overhaul

Add an app-owned backend between DailyBeat and cloud providers. The backend would hold provider credentials, authenticate app users, enforce per-user budgets and rate limits, validate or repair model output, record privacy-safe operational metrics, support provider failover, remotely disable a broken model, and enable staged rollout and rollback. This is appropriate for a larger public product, but adds hosting cost, account/session management, data-protection obligations, backend monitoring, migrations, and a new outage surface. It is deliberately deferred from this release.

## Architecture

### 1. AI integrity and bounded cost

Introduce a pure Kotlin `ReportIntegrityValidator`. It receives the generated text plus the exact visit and event reference counts from `DayContextBuilder.BuiltContext`. It extracts every `[V#]` and `[E#]` token and rejects any reference outside the allowed sets. It also rejects an empty report and a substantive narrative with no valid source citation when source records exist. Structural headings, officer/date labels, and separators are not treated as factual narrative sentences.

Daily generation follows this flow:

1. Build one immutable source context and its allowed reference sets.
2. Request the report with an explicit output-token limit.
3. Validate locally before saving.
4. If invalid, make exactly one correction request containing the validation errors and the same source data.
5. Validate again. Save only a valid result; otherwise return a clear, non-retryable integrity error.

Invalid model structure must not enter WorkManager. Network, timeout, and provider 5xx failures may use bounded background retry. Authentication, model/configuration, provider 4xx, and integrity failures are non-retryable until the user or configuration changes. `ReportGenerator` will not enqueue retry work from inside a worker invocation, removing recursive replacement behavior.

`CloudLlmClient.generate` will require an operation-specific output budget. Initial limits are:

- connection test: 32 tokens;
- event extraction and midday pulse: 400 tokens;
- daily diary: 900 tokens;
- weekly rollup: 1,200 tokens.

DeepSeek/OpenAI-compatible requests will send `max_tokens`; Anthropic will use the same supplied budget. Provider errors will name the selected provider rather than always saying “OpenAI API,” and displayed error bodies will be length-limited and secret-safe.

### 2. Journey map and memory behavior

Today will show a lightweight Compose-native journey preview built from the existing `JourneyMapModel`: ordered stops, route shape, point count, and a clear “Open full map” action. The preview does not initialize MapLibre or fetch tiles.

A dedicated full-map destination will own the MapLibre `MapView`, OpenFreeMap style, route layers, markers, camera fitting, loading/error/retry states, and the existing external OpenStreetMap action. Leaving that destination must pause, stop, and destroy its `MapView`. The normal Today/Diary/History/Settings path therefore avoids the native map engine entirely.

Performance acceptance criteria are evidence-based:

- cold launch and normal Today navigation do not initialize MapLibre;
- three full-map open/close cycles do not show monotonic retained-memory growth or a crash/ANR;
- map failure leaves the journey preview and list usable;
- the full map renders the synthetic journey on the Motorola.

### 3. Deterministic CI and emulator coverage

The existing synthetic-idempotency test will verify seeding and counts only. It will no longer enter a live tile-rendering surface as part of an unrelated assertion. Journey model, URL, citation, retry, and token-budget behavior will be covered with local unit tests and deterministic fake cloud responses.

GitHub Actions will separate fast build/unit/Python checks from emulator instrumentation so a slow emulator does not suppress other evidence. Required CI will not depend on external map tiles becoming fully rendered. The emulator map smoke will verify navigation, container availability, fallback behavior, and return navigation without using Compose operations after a continuously rendering Android view begins. Physical-device QA remains the required real-tile gate. Test reports and screenshots will be uploaded on failure.

### 4. Rendering crash investigation

Map isolation is expected to remove the continuously rendering Android view from routine screen and rotation tests, but no speculative crash fix will be claimed. The QA build will run repeated rotation, background/foreground, home/resume, map open/close, and process-recreation loops with a cleared crash buffer. Any reproduced exception must be traced to its exact stack and fixed test-first. If the prior minified rendering NPE cannot be reproduced after the architecture change, it will be recorded as not reproduced rather than falsely declared impossible.

### 5. Privacy-safe diagnostics and rollback

DailyBeat will keep a bounded local operational-failure record for cloud generation, map loading, backup, and capture failures. Records contain category, timestamp, app version, retryability, and a sanitized message; they contain no API key, raw diary, coordinates, call contents, or model prompt. The existing QA/transparency area will expose this diagnostic summary for support and testing. No data is sent to a third party.

Release notes will identify the last known-good GitHub release and a manual rollback procedure. Release assets retain the universal signed APK and SHA-256 checksum required by Obtainium. A failed post-install smoke blocks publication rather than relying on rollback after release.

## Testing strategy

- Test-first unit cases for nonexistent citations, missing citations, valid mixed references, zero visits, retry exactly once, retry classification, and every token budget.
- Deterministic mock responses for valid output, malformed output, repeated invalid output, 401, 429, 500, timeout, and empty content.
- Local Locust testing only against the mock DeepSeek-compatible server; never load-test the billable provider.
- Android unit tests, Python tests, lint, debug/release assembly, instrumentation, ABI inspection, signature verification, and secret scan.
- Three Motorola passes through onboarding/Today/Diary/History/Settings, synthetic day, full map, report generation state, background/resume, and crash/ANR inspection using the QA package.
- After all mock and device gates pass, one final real DeepSeek daily generation with the 900-token cap. Its references must all exist in the supplied context before it may be saved.

## Release sequencing

1. Finish and merge the independently reviewed Journey Beat icon PR.
2. Land the hardening work through a separate PR with green required checks.
3. Rebase/merge against the final `main`, bump the version, and update the changelog and honest audit rating.
4. Build the signed APK through GitHub Actions, verify certificate/checksum/ABI metadata, and install-update it on the Motorola.
5. Publish exactly one stable GitHub release asset plus checksum and confirm Obtainium compatibility.

## Success criteria

- No report with nonexistent citation IDs can be saved.
- Invalid AI output causes at most one correction request and no background billing loop.
- Every cloud request has an explicit output-token cap.
- Required CI is deterministic and green; failures retain actionable artifacts.
- Normal app navigation does not load MapLibre; the dedicated map works on the Motorola.
- Three device loops show no DailyBeat crash or ANR.
- The signed release APK upgrades successfully and contains no secret.
- Remaining field-validation and external-service risks are stated explicitly, not hidden behind a production-ready claim.
