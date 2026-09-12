# DailyBeat: end-to-end build handoff

This file is the active handoff for Claude Code. Work autonomously through the plan below, keep the user’s production data safe, and leave an evidence-backed QA build. Do not create or publish a production release unless the user explicitly asks for that after reviewing the QA result.

## Mission

Turn the approved DailyBeat v3.9 product direction and browser prototype into the best production-quality Android implementation this repository can support. Start by comparing the prototype with the released Compose UI. Preserve good existing work; implement only real gaps or material improvements. The outcome is a coherent end-to-end application, not a disconnected visual reskin.

## Verified starting point — 2026-09-12

- Repository: `https://github.com/sampathmannam/dailybeat`
- Working branch: `claude/end-to-end-ui`, created from `origin/main`
- Starting commit: `103f9039df70d964a71ec68c2e81d9ca2e89fcdf`
- Stable tag/release: `v3.9.0`
- Android package: `com.dailybeat.app`
- Stable version: `versionCode 20`, `versionName 3.9.0`
- Release APK SHA-256: `30ce22bf2c2a95d84a84b4670cc9a28a82f82a4f0eca40b90dd7fe6ffb5b83e4`
- PR #37 and every release gate passed: build, unit tests, lint, release policy, online backup round trip, offline instrumentation, regular instrumentation, dependency review, and CodeQL.
- There are no open product issues. Open Dependabot PRs are separate maintenance work and are not part of this build; do not merge them in bulk.
- The old Figma link in `docs/DESIGN.md` exists, but its connected Starter workspace hit the Figma MCP quota. Do not block this work on Figma.

## Design packet

These artifacts are outside the Git repository but available on this machine. Read them before changing UI code:

- Interactive prototype: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/dailybeat-product-prototype.html`
- Product design board: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/dailybeat-product-design-board.png`
- Phone reference: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/dailybeat-prototype-phone.png`
- Tablet reference: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/dailybeat-prototype-tablet.png`
- Portable tokens: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/dailybeat-design-tokens.json`
- Design handoff: `/Users/sujithsampath/Documents/Codex/2026-09-12/see-daily-beat-is-developing-by/outputs/DAILYBEAT_DESIGN_ALTERNATIVE.md`

Repository sources of truth:

- Product doctrine: `PRODUCT.md`
- Experience direction: `docs/DESIGN.md`
- Reliability baseline and known risks: `docs/AUDIT_RATING.md`
- Release contract: `docs/RELEASE.md`
- Current functionality: `README.md` and `CHANGELOG.md`

The released Compose implementation remains authoritative for real behavior, privacy, storage, and Android semantics. The prototype is authoritative for design intent, hierarchy, adaptive composition, and interaction direction. When they differ, reconcile them deliberately and document the decision.

## Non-negotiable product and safety constraints

1. DailyBeat is a private journey ledger for one officer, not a social feed, sports tracker, leaderboard, employee-surveillance surface, or generic dashboard.
2. Raw GPS breadcrumbs, visits, and notes remain local unless a deliberate backup, AI, export, or share action says otherwise.
3. Raw coordinates never enter cloud-report prompts. Private zones and review-hidden stops stay excluded from cloud reports, geocoding, and shared exports. Preserve the existing regression tests.
4. Never log credentials, authorization headers, prompts, raw coordinates, or diary content. Do not add analytics or telemetry without an explicit privacy design and user authorization.
5. Do not modify, expose, or delete retained legacy DSR data. DSR is a separate app. PatrolGrid is also out of scope.
6. Do not use destructive Room migration fallback. Any schema bump must include additive migrations and upgrade-path tests for every released version.
7. Production package data must never be touched by automated tests. Debug is `.qa`; destructive instrumentation is `.qa.e2eloop` only.
8. Do not bundle provider keys. Cloud AI keys are configured at runtime and encrypted on-device. Supabase client configuration comes from build-time secrets.
9. Preserve the permanent release signing identity. Never generate or substitute a production keystore and never ask the user to uninstall the stable app.
10. Do not broaden dependencies, upgrade AGP/Kotlin/Gradle, or merge Dependabot work while implementing this design. Dependency changes require a separate, focused compatibility task.

## Product-quality bar

- Calm, trustworthy, purposeful visual voice.
- Deep navy structure, signal-yellow journey language, quiet light/dark surfaces, and Android system sans/Roboto.
- One obvious next action on Today: `Review my day`.
- The map is proof. Never fabricate a connected route; honest empty/loading/offline states are mandatory.
- Compact widths use bottom navigation; widths at 600dp and above use a navigation rail with centered readable content.
- Minimum 48dp interactive targets, TalkBack labels and headings, predictable Android Back, system insets, 200% font scaling, dark theme, landscape behavior, and reduced-motion compatibility.
- Never communicate health, reliability, privacy, or completion by color alone.
- Use restrained motion only to explain sheet entry, selection, route replay, or review progression.

## End-to-end execution plan

### Phase 0 — establish a clean baseline

1. Confirm this branch is based on `origin/main` at the starting commit above and inspect the worktree before editing.
2. Read all source-of-truth documents and the design packet.
3. Run the policy and Android baseline gates:

   ```bash
   python3 -m pytest scripts/tests/ -q
   cd android
   ./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --stacktrace
   ```

4. Record the baseline result and any environment-only limitation. A missing physical device or secret is not a reason to weaken or delete a gate.

### Phase 1 — audit before implementation

Create `docs/IMPLEMENTATION_MATRIX.md`. For every prototype state—Today, Days, Insights, Settings, Review, Add Moment, capture pause/resume, phone/tablet, and light/dark—map:

- prototype behavior and visual intent;
- current Compose implementation and owning file;
- state/data source and navigation route;
- accessibility semantics;
- status: already matched, intentional production difference, or gap;
- exact change and test needed for every gap.

Start with these code areas:

- Foundations: `android/app/src/main/java/com/dailybeat/app/ui/theme/`
- Adaptive shell: `ui/DailyBeatAppScaffold.kt`
- Today: `ui/today/TodayScreen.kt` and `TodayViewModel.kt`
- Days: `ui/feed/FeedScreen.kt`, `DayFeedItem.kt`, and route-thumbnail components
- Insights: `ui/insights/InsightsScreen.kt` and `InsightsViewModel.kt`
- Settings: `ui/settings/SettingsScreen.kt` and `SettingsViewModel.kt`
- Review: `ui/review/ReviewDayScreen.kt` and `ReviewDayViewModel.kt`
- Map evidence: `ui/map/` and `ui/components/JourneyMap*`
- Capture status: `capture/`, especially `CaptureController.kt`, `CaptureHealthStore.kt`, and `LocationService.kt`

Do not create code churn for prototype elements the real app already implements better.

### Phase 2 — foundations and adaptive shell

1. Reconcile `dailybeat-design-tokens.json` with `Color.kt`, `Theme.kt`, `Type.kt`, and `Shape.kt`. Prefer semantic tokens over screen-local magic values.
2. Preserve Material 3 behavior and Android system typography. Do not introduce a web font.
3. Make the responsive shell deterministic at compact and expanded widths. Retain bottom navigation below 600dp and the navigation rail at/above 600dp.
4. Verify edge-to-edge system insets, gesture/navigation-bar clearance, keyboard/IME behavior, and Android Back.
5. Keep motion short, interruptible, and disabled when the platform animator scale is disabled.

Tests: theme preference persistence, navigation route semantics, compact/expanded behavior, minimum target sizes, and no overlap at 200% font scaling.

### Phase 3 — implement the core journey-ledger screens

Work in vertical slices so each screen remains functional after every commit.

1. **Today**
   - Date/purpose, honest map proof, consolidated capture overview, one Beat summary, primary review action, secondary Add Moment/Diary actions, and chronological moments.
   - Keep recent-fix age, GPS reliability, capture gaps, and cloud readiness factual and driven by real state.
   - Empty, loading, offline, disabled, paused, degraded, and populated states must all have recovery copy.

2. **Days**
   - Map-led day records with Beat title, date, route evidence, distance, tracked time, stop count, and truthful review status.
   - Tapping a normal day opens its journey map; review remains an explicit action, not an accidental navigation side effect.

3. **Insights**
   - Preserve private, deterministic 28-day insights and the actionable recommendation.
   - Chart selection must work with touch, keyboard/accessibility focus, and TalkBack; values and labels must not rely on color.

4. **Settings**
   - Order capture/privacy and named places before appearance, Cloud AI, and backup.
   - Preserve the System/Light/Dark selector, current-location place capture, private-zone enforcement, and explicit service status.
   - Keep technical provider/base-URL details subordinate to normal user tasks.

5. **Review my day**
   - Keep rename, hide/restore, correction, Beat naming, and completion reversible and fast.
   - Show uncertainty and capture gaps honestly. Never delete raw captured history as a side effect of UI correction.

6. **Add Moment and Diary**
   - Use a focused sheet/dialog that survives rotation or process recreation where appropriate.
   - Save user text locally before any best-effort cloud structuring, and preserve intentional clearing.

For each slice, update or add focused unit/presentation tests before moving on.

### Phase 4 — verify real feature plumbing

Run explicit end-to-end reasoning through these paths; do not validate only screenshots:

- first launch → onboarding → incremental permissions → Today;
- background capture → filtered breadcrumb → visit detection → named place → map/Today/Days;
- one-hour privacy pause → process recreation/reboot handling → automatic resume;
- Add Moment/voice transcript → local event → optional cloud structure → diary;
- Review rename/hide/restore/complete → Days/Insights/report/export consistency;
- private zone → no geocoder call and exclusion from report/export while retaining backup privacy metadata;
- Cloud AI unavailable/invalid/slow/malformed → bounded, recoverable errors without losing local data;
- backup sign-in → upload → download → validate → transactional restore;
- offline map/provider → usable local route list, diary, review, and export;
- existing v3.9 database → any new schema → data-preserving migration.

If a product behavior requires a model or database change, implement repository/view-model/UI/tests as one vertical slice.

### Phase 5 — quality gates

Minimum local gate after implementation:

```bash
python3 -m pytest scripts/tests/ -q
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --stacktrace
./gradlew assembleDebug assembleDebugAndroidTest \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop \
  --no-daemon --stacktrace
```

When an emulator or disposable physical device is available, run:

```bash
./gradlew connectedDebugAndroidTest \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop \
  --no-daemon --stacktrace
```

For a connected physical phone, prefer the repository’s guarded runner:

```bash
DAILYBEAT_BRANCH=claude/end-to-end-ui \
  ./scripts/mac_phone_e2e.sh YOUR_ADB_SERIAL
```

Visual/device matrix:

- compact phone, normal text, light and dark;
- compact phone, 200% text;
- landscape/short viewport;
- expanded tablet width with navigation rail;
- onboarding, Today states, Days, Insights, Settings, Review, Add Moment/keyboard, map loading/offline/populated;
- TalkBack headings, labels, traversal order, state announcements, and 48dp targets;
- system Back from sheets, Review, full map, and keyboard-visible states.

Do not update golden screenshots to hide a regression. Explain every intentional visual delta.

### Phase 6 — QA artifact and review handoff

1. Produce an isolated QA APK. It must install as `.qa` or `.qa.e2eloop`, never `com.dailybeat.app`.
2. Copy the final QA APK and verification evidence to a clearly named output directory without overwriting the stable v3.9 APK.
3. Create `docs/CLAUDE_BUILD_REPORT.md` containing:
   - outcome first;
   - commits and files changed;
   - prototype-to-production decisions;
   - test counts and exact commands;
   - APK path, package, version, size, and SHA-256;
   - screenshots/evidence paths;
   - remaining risks, unrun gates, and why;
   - recommended next action.
4. Leave the branch clean and reviewable. Do not merge or push unless the user requests it.

### Phase 7 — production release only after explicit authorization

If and only if the user asks for a new release after QA approval:

1. Choose a new semantic version; never republish or move `v3.9.0`.
2. Update `android/app/build.gradle.kts`, `release/version.txt`, README, changelog, release documentation, tests, and a new `release/requests/<version>.txt` together.
3. Open a focused PR to protected `main`.
4. Require build, unit/lint, release policy, live backup, offline instrumentation, regular instrumentation, dependency review, and CodeQL to pass on the protected commit.
5. Let `.github/workflows/publish-release.yml` build with the permanent CI signing key and verify certificate fingerprint `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`.
6. Verify the GitHub Release asset name, SHA-256, package/version, signing certificate, and install-over-existing behavior. Never advise uninstalling the stable app.

## Definition of done for this build

- `docs/IMPLEMENTATION_MATRIX.md` explains every prototype/production decision.
- The production Compose UI is coherent across all top-level screens and the Review/Add Moment flows.
- Phone/tablet, light/dark, large-text, insets, keyboard, TalkBack, and Back behavior are verified proportionally to available devices.
- Real data/state drives every status and route; no fake precision or simulated capture is shipped.
- Privacy invariants, private-zone enforcement, migrations, backup validation, and QA package isolation remain tested.
- Python policy tests, Android debug build, JVM tests, lint, and instrumentation compilation pass.
- Device tests are run when a disposable emulator/device exists; otherwise the missing evidence is explicit.
- A QA APK and `docs/CLAUDE_BUILD_REPORT.md` are delivered.
- Stable v3.9 artifacts, production signing, and production app data remain untouched.
- No production tag, release, merge, or push occurs without explicit user authorization.

## First action

Begin with Phase 0 and Phase 1. Do not start by rewriting screens. Establish the green baseline, inspect the prototype and current Compose UI, create the implementation matrix, then execute the smallest high-value vertical slice.

