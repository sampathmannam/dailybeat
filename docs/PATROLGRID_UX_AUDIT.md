# PatrolGrid native app audit

Audit date: 2026-09-02

Baseline commit: `61342b5` (`Keep invite-only email authentication testable`)

Target: Android phone, tablet, foldable, and multi-window; patrol personnel and subdivision supervisors.

Method: source-level native audit against the PatrolGrid product contract, Material 3 conventions, and the Impeccable native audit rubric. The audit covers accessibility, performance, appearance/theming, Android platform conformance, adaptivity, core task completion, and the automated UI evidence available at the baseline commit.

> **Baseline before fixes.** Every code path and line anchor below is pinned to commit `61342b5`. Changes made after that commit are intentionally not credited here. This report is a backlog and historical baseline, not a certification of the post-fix worktree. Re-run the audit after fixes before changing scores or closing findings.

## Audit health score

| # | Dimension | Score | Key finding |
|---|---|---:|---|
| 1 | Accessibility | 2/4 | Compose semantics and scalable type are present, but small operational status colors fail contrast and several compound radio rows expose duplicate actions. |
| 2 | Performance | 3/4 | High-volume mission surfaces use `LazyColumn` and network work is dispatched off the main thread; the unit list is eager and the map redraws as many as 1,000 individual segments. |
| 3 | Appearance & Theming | 2/4 | A coherent light/dark Material scheme exists, but appearance is forced by role rather than environment and raw status colors fail the light-theme contrast floor. |
| 4 | Platform Conformance | 3/4 | The app is recognizably Android and uses Material 3 components, system Back, snackbars, sheets, and insets; dead arrow affordances and compact-only navigation prevent a fully fluent result. |
| 5 | Adaptivity | 1/4 | There is no window-size strategy, navigation rail, content-width cap, or complete large-font/IME handling; at least one production list is not scrollable. |
| **Total** |  | **11/20** | **Acceptable — significant work needed before rollout** |

Rating bands: 18–20 Excellent, 14–17 Good, 10–13 Acceptable, 6–9 Poor, 0–5 Critical.

The previous `91/100` score and “no open P1” claim were not supported by the baseline code. In particular, they did not account for the server-backed review filter, the absence of review submission, non-selectable mission evidence, or the lack of geographic context in the production route view.

## Platform conformance verdict

**Native verdict: PASS. Production-readiness verdict: FAIL at this baseline.**

PatrolGrid reads as a native Android app, not a ported website. It uses Compose, Material 3 navigation, tabs, buttons, bottom sheets, snackbars, semantic typography, and standard system permission flows. System Back is not hijacked, and `Scaffold` owns the top and bottom surfaces (`android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:157`).

The native shell is not yet a complete operational tool. The same phone bottom bar is rendered at every width (`PatrolGridAppScaffold.kt:162`), a chevron announces “Open mission” without a click action (`PatrolControlScreen.kt:311` and `PatrolControlScreen.kt:345`), and production route evidence is drawn as a normalized line with no street, beat boundary, priority coordinates, or geographic labels (`PatrolRouteMap.kt:97`). Those issues make the interface feel unfinished during the exact supervisor tasks PatrolGrid is meant to support.

## Executive summary

- Audit health score: **11/20 (Acceptable)**.
- Issues found: **3 P0, 8 P1, 8 P2, and 2 P3**.
- The highest-risk defect is a server-backed state mismatch: `needs_review` missions are mapped correctly, then excluded from `activeMissions`, while the attention queue only searches `activeMissions`. A production supervisor can therefore miss the missions that most need review.
- Supervisors cannot select a mission, inspect evidence for each unit, or submit a human review outcome, despite all three being core product promises and despite the database already supporting review records.
- The production “map” proves that points form a trail but does not show where that trail occurred. It cannot yet answer the user’s core question: “Where did this unit patrol?”
- Patrol personnel can tap Observation or Deviation, but the app saves generic canned text rather than the officer’s actual context.
- Material structure, explicit tracking boundaries, encrypted local evidence, offline outbox behavior, honest route truncation disclosure, and non-punitive language are strong foundations worth preserving.

## Detailed findings by severity

### P0 — blocking

#### P0.1 Server-backed missions needing review disappear from the supervisor queue, and no review can be submitted

- **Location:** `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:489`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:375`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:59`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:171`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/PatrolGridRemote.kt:43`.
- **Category:** Core task completion / Conformance.
- **Evidence:** The client maps backend `needs_review` to `PatrolMissionStatus.NEEDS_REVIEW`, but `applyRemoteSnapshot` retains only `ACTIVE`, `PAUSED_WITH_REASON`, and `ASSIGNED` in `activeMissions`. `PatrolControlScreen` then derives `needsAttention` only from `activeMissions`. The Needs review tab renders one read-only `ReviewPanel`, and the remote interface has no review submission operation. The database already accepts `approved`, `needs_context`, and `technically_inconclusive` reviews (`supabase/migrations/202609010001_patrolgrid_core.sql:195` and `supabase/migrations/202609010001_patrolgrid_core.sql:1091`).
- **Impact:** A supervisor can miss completed missions requiring context and cannot close any review in the app. This prevents a core workflow described in `PRODUCT.md:15` and `PRODUCT.md:19`.
- **Guideline:** A primary task must be discoverable, complete, reversible where appropriate, and represented consistently across data and UI state.
- **Recommendation:** Preserve review-eligible missions in a dedicated queue; display every pending review; expose evidence, officer updates, GPS confidence, and prior reviews; then allow a supervisor to submit an explicit human outcome with notes and optimistic-concurrency protection.
- **Suggested command:** `$impeccable harden PatrolGrid supervisor review`.

#### P0.2 Mission selection and drill-down are absent, so evidence is effectively limited to one implicit mission

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:353`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:374`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:139`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:345`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:372`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:64`.
- **Category:** Core task completion / Accessibility / Conformance.
- **Evidence:** Mission rows are non-clickable `Surface`s. Active mission cards draw a chevron with the content description “Open mission” but provide no `onClick`. The ViewModel silently chooses the locally active mission or the first mission. The remote snapshot fetches route points for that single choice and returns only one shared route-point list.
- **Impact:** Patrol personnel with multiple assignments cannot deliberately select the mission they are starting. Supervisors cannot move from unit to unit or inspect the route, checkpoints, updates, and review state for a chosen mission. TalkBack users hear an action that does not exist.
- **Guideline:** Material affordances must be truthful; a chevron implies navigation. List-detail workflows must keep selection explicit, visible, and restorable.
- **Recommendation:** Add a mission-detail destination and selected-mission state. Make mission and attention rows real 48 dp targets, load evidence by mission ID, preserve selection across rotation/process recreation, and provide an unambiguous Back path.
- **Suggested command:** `$impeccable shape PatrolGrid mission detail`.

#### P0.3 Production route evidence has no geographic context

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolRouteMap.kt:56`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolRouteMap.kt:97`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:65`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:186`, `android/app/src/main/java/com/dailybeat/app/data/model/PatrolModels.kt:19`.
- **Category:** Core task completion / Appearance.
- **Evidence:** In production mode the Canvas normalizes latitude/longitude to its own bounds and draws only the line plus start/latest dots. The priority query omits latitude, longitude, radius, and accuracy; the assignment-options query omits `route_geojson`; and `PatrolRoutePlan` contains only names and a duty-window label. The backend already stores route GeoJSON and priority coordinates (`supabase/migrations/202609010001_patrolgrid_core.sql:29` and `supabase/migrations/202609010001_patrolgrid_core.sql:46`).
- **Impact:** A supervisor can see a shape but cannot tell which road, ward, boundary, checkpoint, or patrol area it represents. This blocks the product success criterion in `PRODUCT.md:21`.
- **Guideline:** A map must preserve spatial meaning. Route evidence requires a geographic reference, uncertainty, and a clear distinction between planned and recorded paths.
- **Recommendation:** Load planned route geometry and priority coordinates; render recorded evidence on a lifecycle-safe native map with offline/error fallback; label start/end, required priorities, accuracy or low-confidence segments, and the “latest 1,000 points” boundary. Keep the map mission-selected rather than surveillance-first.
- **Suggested command:** `$impeccable shape PatrolGrid geospatial evidence`.

### P1 — major

#### P1.1 Observation and deviation actions save canned text, not field context

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/MyPatrolScreen.kt:359`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:159`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:183`.
- **Category:** Core task completion / Accessibility.
- **Evidence:** Tapping either button immediately enqueues “Observation recorded by patrol personnel” or “Operational deviation recorded; supervisor context required.” There is no editor, category-specific prompt, attachment, or review-before-save step even though the backend accepts up to 4,000 characters (`supabase/migrations/202609010001_patrolgrid_core.sql:178`).
- **Impact:** The app records that context exists while discarding the context itself, weakening the evidence and forcing follow-up outside PatrolGrid.
- **Recommendation:** Add a focused, keyboard-safe field-update editor with a meaningful minimum, optional current location/accuracy disclosure, save progress, cancellation, and offline confirmation. Do not pre-label a deviation as wrongdoing.
- **Suggested command:** `$impeccable clarify PatrolGrid field updates`.

#### P1.2 Ending a patrol always records “completed” and offers no reason or confirmation

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/MyPatrolScreen.kt:391`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:214`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/PatrolTrackSyncer.kt:52`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/PatrolGridRemote.kt:52`.
- **Category:** Core task completion / Error prevention.
- **Evidence:** End patrol is a single immediate action. The delayed close calls `endSession(sessionId)` and therefore uses the default `completed`, although the API supports `relieved`, `cancelled`, and `device_issue`.
- **Impact:** Accidental taps or device/safety interruptions can be written as completed duty, corrupting the operational record.
- **Recommendation:** Use an interruptive confirmation only for this consequential action. Show pending priorities and unsynced evidence; require an end reason when not completed; keep tracking-off feedback immediate and persist the chosen reason through offline close sync.
- **Suggested command:** `$impeccable harden PatrolGrid end-patrol flow`.

#### P1.3 Patrol Control’s Units destination cannot support coordination and may clip longer rosters

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:397`.
- **Category:** Performance / Adaptivity / Core task completion.
- **Evidence:** The screen uses a non-scrollable `Column` and eager `forEach`. Each row shows only unit name and personnel count; it has no current mission, duty state, last update, exception state, or drill-down.
- **Impact:** A roster taller than the viewport is unreachable, and even visible units do not answer whether or where they are patrolling.
- **Recommendation:** Use a keyed `LazyColumn`, status-aware rows, empty/loading/error states, search only if roster size justifies it, and route rows into the selected mission or unit detail.
- **Suggested command:** `$impeccable adapt PatrolGrid units`.

#### P1.4 Route-template, priority, and unit management have no app workflow

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolAssignmentSheet.kt:78`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:184`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/PatrolGridRemote.kt:49`.
- **Category:** Core task completion.
- **Evidence:** Assignment can choose existing route and unit options, but the remote interface exposes only loading and assignment. There is no create/edit/archive route, map boundary, checkpoint/radius, unit, or membership workflow, although supervisor RLS permits route-template and priority management (`supabase/migrations/202609010001_patrolgrid_core.sql:832` and `supabase/migrations/202609010001_patrolgrid_core.sql:855`).
- **Impact:** A subdivision cannot configure or revise its own operating plan from PatrolGrid; rollout depends on manual database administration.
- **Recommendation:** Build a supervisor-only route-library workflow with explicit save/publish semantics, geospatial validation, default guidance, duty window, priorities, and audit history. Keep account provisioning and sensitive membership changes in an appropriately managed administrative surface.
- **Suggested command:** `$impeccable shape PatrolGrid route library`.

#### P1.5 Small operational statuses fail required contrast in the supervisor light theme

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/theme/Color.kt:6`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:280`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:290`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:304`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:338`.
- **Category:** Accessibility / Theming.
- **Evidence:** Verified sRGB contrast ratios on `SurfaceCard #FFFFFF`: Gold `#E8A317` is **2.17:1**, Success `#16A34A` is **3.30:1**, and warning text `#B66F00` is **3.99:1**. The 12 sp status labels require 4.5:1; the Gold warning icon also misses the 3:1 non-text threshold. `InkMuted #64748B` on `Canvas #F8F7F4` is **4.44:1**, just below the 4.5:1 body-text floor.
- **Impact:** Low-vision staff and people using the app in glare can miss the difference between active, needs-context, and completed states.
- **Recommendation:** Introduce semantic status roles with separate foreground/container colors for light and dark themes. Recalculate every role at 4.5:1 for small text and 3:1 for meaningful icons/boundaries; retain text and icon cues so color is never the sole signal.
- **Suggested command:** `$impeccable audit PatrolGrid contrast`.

#### P1.6 Navigation and content never adapt beyond compact-phone structure

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:120`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:162`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolRouteMap.kt:58`.
- **Category:** Adaptivity / Platform Conformance.
- **Evidence:** There is no `WindowSizeClass`, adaptive scaffold, navigation rail, two-pane list/detail layout, or maximum content width. The phone bottom bar and a full-width 1.9:1 map are rendered on tablets, landscape, foldables, and multi-window.
- **Impact:** The tablet experience becomes a stretched phone UI, wastes space needed for mission/evidence comparison, and diverges from Material 3 navigation guidance.
- **Recommendation:** Use compact/medium/expanded window classes, bottom navigation on compact widths, rail or drawer on wider widths, and list-detail where mission selection and evidence can coexist. Test resizable windows rather than device names.
- **Suggested command:** `$impeccable adapt PatrolGrid navigation and layout`.

#### P1.7 Sign-in and onboarding are not scrollable or proven at large font and IME sizes

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/auth/PatrolGridLoginScreen.kt:47`, `android/app/src/main/java/com/dailybeat/app/ui/onboarding/OnboardingScreen.kt:48`, `android/app/src/main/java/com/dailybeat/app/ui/onboarding/OnboardingScreen.kt:83`.
- **Category:** Accessibility / Adaptivity.
- **Evidence:** Both entry surfaces use a centered, full-height `Column` with fixed padding and no vertical scroll. Onboarding step 1 combines logo, title, text field, two role cards, and a primary action. No font-scale, landscape, or IME instrumentation coverage exists.
- **Impact:** At 200% font scale, short landscape windows, or split-screen, the sign-in/continue action can move behind the keyboard or outside the reachable viewport, preventing entry.
- **Recommendation:** Use IME-aware scrolling and focus navigation, preserve draft state, test 200% font scale and minimum supported window height, and keep the primary action reachable without relying on keyboard dismissal.
- **Suggested command:** `$impeccable adapt PatrolGrid entry flows`.

#### P1.8 Automated native coverage does not exercise every production-critical control or state

- **Location:** `maestro/onboarding_and_nav.yaml:26`, `maestro/supervisor_assignment.yaml:21`, `android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt:58`.
- **Category:** Platform quality / Adaptivity.
- **Evidence:** The patrol Maestro flow starts, records one generic observation, ends, and opens Missions. The supervisor flow assigns one mission. Neither flow covers priority visits, deviation detail, mission selection, review outcomes, end reasons, sign-in failure/session expiry, sign-out guards, denied-permission recovery, cached/offline state, retry, tablet/landscape, large text, dark/light appearance, or TalkBack semantics. The instrumentation navigation test explicitly treats the unfinished Messages placeholder as a passing destination (`MainNavigationTest.kt:66`).
- **Impact:** Green tests can coexist with dead affordances and blocked production workflows, which is exactly what happened in this baseline.
- **Recommendation:** Build a role-by-role control inventory and require one successful path plus cancellation/error/offline coverage for every consequential action. Add screenshot/semantic checks across compact and expanded windows, 200% font, both appearances, permission states, and process recreation.
- **Suggested command:** `$impeccable harden PatrolGrid test matrix`.

### P2 — minor

#### P2.1 Compound radio rows expose two click targets for one choice

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/onboarding/OnboardingScreen.kt:151`, `android/app/src/main/java/com/dailybeat/app/ui/onboarding/OnboardingScreen.kt:161`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolAssignmentSheet.kt:173`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolAssignmentSheet.kt:199`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:433`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:445`.
- **Category:** Accessibility.
- **Impact:** TalkBack can stop on both the selectable row and its nested radio button, doubling focus and making the option/state relationship less clear.
- **Recommendation:** Make the row the single selectable target, set the child `RadioButton` click handler to `null`, and provide merged `Role.RadioButton` / selected semantics with one label.
- **Suggested command:** `$impeccable audit PatrolGrid semantics`.

#### P2.2 PatrolGrid operational copy is almost entirely hardcoded English

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:122`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/MyPatrolScreen.kt:75`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolControlScreen.kt:74`, `android/app/src/main/java/com/dailybeat/app/ui/auth/PatrolGridLoginScreen.kt:67`.
- **Category:** Accessibility / Adaptivity.
- **Impact:** Translation, plural handling, terminology updates, and meaningful RTL validation are blocked even though the manifest declares `supportsRtl=true` (`android/app/src/main/AndroidManifest.xml:22`).
- **Recommendation:** Move user-visible copy and plurals to resources, avoid concatenated sentences, and test at least one long-string locale and one RTL locale.
- **Suggested command:** `$impeccable adapt PatrolGrid localization`.

#### P2.3 Duty windows use device timezone and a fixed English pattern

- **Location:** `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:465`, `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:484`.
- **Category:** Adaptivity / Correctness.
- **Impact:** A device with a wrong/travel timezone can show a different duty window from the subdivision schedule, and `dd MMM · HH:mm` is not locale-aware.
- **Recommendation:** Carry subdivision timezone from the server, format with the user locale, show the zone when ambiguity matters, and test overnight and daylight-saving boundaries.
- **Suggested command:** `$impeccable harden PatrolGrid date and time`.

#### P2.4 Appearance is forced by role rather than system preference or duty context

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:73`, `android/app/src/main/java/com/dailybeat/app/ui/theme/Theme.kt:52`.
- **Category:** Appearance & Theming.
- **Impact:** All patrol personnel receive dark UI even during bright daytime duty, while supervisors always receive light UI during night operations. There is no system/default override.
- **Recommendation:** Default from system appearance, optionally offer System/Light/Dark, and preserve the intentional high-contrast night scheme without tying it to authorization role.
- **Suggested command:** `$impeccable colorize PatrolGrid appearance roles`.

#### P2.5 Missions are capped at 100 with no paging or history state

- **Location:** `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:57`.
- **Category:** Performance / Core task completion.
- **Impact:** A long-running subdivision silently loses older mission history, and the UI cannot distinguish “all loaded” from “first page loaded.”
- **Recommendation:** Query by explicit state/window, add cursor paging with loading/end/error states, and keep the active/needs-review queues independent from archive history.
- **Suggested command:** `$impeccable harden PatrolGrid mission paging`.

#### P2.6 Managed authentication has no explicit supervisor-led recovery action

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/auth/PatrolGridLoginScreen.kt:101`, `android/app/src/main/java/com/dailybeat/app/ui/auth/PatrolGridLoginScreen.kt:107`.
- **Category:** Accessibility / Core task completion.
- **Impact:** Staff who cannot authenticate receive static “contact your supervisor” text but no explicit, accessible direction to the existing official Department channel.
- **Recommendation:** Explain the supervisor-led managed-account recovery path without enabling public account creation or creating a separate technical-support desk; avoid revealing whether an account exists.
- **Suggested command:** `$impeccable onboard PatrolGrid access recovery`.

#### P2.7 Debug navigation deliberately exposes unfinished destinations

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:132`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:146`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:454`.
- **Category:** Conformance / Product completeness.
- **Impact:** Alerts and Messages lead to “prepared for the next build slice.” This is hidden in server-backed production, but it makes QA navigation look complete while testing a placeholder instead of a workflow.
- **Recommendation:** Remove placeholder destinations from executable QA coverage until a slice has a real task and acceptance criteria, or build the smallest complete alerts/messages workflow before exposing it.
- **Suggested command:** `$impeccable distill PatrolGrid navigation`.

#### P2.8 More does not disclose synchronization health

- **Location:** `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridAppScaffold.kt:285`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolGridViewModel.kt:29`.
- **Category:** Accessibility / Operational feedback.
- **Impact:** Staff cannot see last successful sync, pending field updates, pending route points, cache age, or an actionable persistent failure state from a stable destination.
- **Recommendation:** Add concise sync health in More using plain language, a retry action, and escalation copy for the existing supervisor/command channel; do not expose sensitive coordinates in diagnostic copy or imply a separate help desk.
- **Suggested command:** `$impeccable clarify PatrolGrid sync status`.

### P3 — polish

#### P3.1 Route drawing performs per-point normalization and up to 999 individual segment draws on recomposition

- **Location:** `android/app/src/main/java/com/dailybeat/app/patrolgrid/SupabasePatrolGridClient.kt:82`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolRouteMap.kt:97`, `android/app/src/main/java/com/dailybeat/app/ui/patrol/PatrolRouteMap.kt:111`.
- **Category:** Performance.
- **Impact:** Dense routes can add avoidable work during unrelated UI-state updates, especially on lower-end field devices.
- **Recommendation:** Cache transformed geometry by mission/evidence revision, draw one `Path`, and simplify points by zoom/viewport while preserving the full evidence server-side.
- **Suggested command:** `$impeccable optimize PatrolGrid route rendering`.

#### P3.2 The native release evidence has no Baseline Profile or measured startup/frame benchmark

- **Location:** `android/app/build.gradle.kts:90`.
- **Category:** Performance.
- **Impact:** Unit, instrumentation, backend, and load tests do not quantify cold startup or field-screen frame timing on representative lower-end hardware.
- **Recommendation:** Add Macrobenchmark/Baseline Profile coverage for secure entry, My Patrol, Patrol Control, mission detail, and dense-route rendering; record thresholds in the rollout gate.
- **Suggested command:** `$impeccable optimize PatrolGrid startup`.

## Patterns and systemic issues

1. **The data model is ahead of the UI.** Route GeoJSON, checkpoint coordinates, three review outcomes, session end reasons, and supervisor RLS already exist in Supabase, but the Android remote contract and screens expose only a narrow subset. Build vertical slices from database capability through remote contract, state, UI, error handling, and E2E evidence.
2. **“Primary mission” is overloaded.** One implicit mission drives patrol start, supervisor map, review summary, point counts, and route evidence. This prevents multi-mission patrol and supervisor workflows. Replace it with explicit selected/active mission identities.
3. **Demo success hides server-backed gaps.** Local repository data includes review-like states and placeholder destinations, while production filtering behaves differently. Critical UI tests need authenticated, server-backed fixtures or a contract-faithful fake.
4. **Field evidence actions optimize taps over meaning.** Visit, observation, deviation, and end all save immediately with fixed notes or reasons. Fast actions are valuable, but consequential evidence needs user context, confirmation proportional to risk, and a visible offline receipt.
5. **Compact phone assumptions are global.** Bottom navigation, centered entry columns, full-width maps, and non-scrollable utility surfaces do not branch by window size or font scale.
6. **Status color is not yet a semantic system.** Gold, green, blue, and warning brown are chosen locally and reused for text, icons, and map marks even when their contrast requirements differ.
7. **Automated checks assert presence more often than completion.** A screen title or placeholder appearing is treated as success. Tests need to prove that data changes, survives restart/offline sync, and is visible to the correct role.

## Positive findings

- PatrolGrid’s mission-first and exception-first information architecture matches the product’s non-surveillance positioning (`PRODUCT.md:35`).
- Tracking state is visible, starts from an explicit mission action, and ends visibly; sign-out is guarded while active or while secure synchronization is pending (`PatrolGridViewModel.kt:243`).
- Local route points, the mutation outbox, and the cached snapshot fail closed through device-bound encryption; production networking rejects cleartext traffic (`android/app/src/main/AndroidManifest.xml:24`).
- The latest-1,000 route-point boundary is disclosed in both Canvas semantics and the legend (`PatrolRouteMap.kt:60` and `PatrolRouteMap.kt:189`) instead of implying that a bounded view is the complete evidence record.
- High-volume patrol and control content uses keyed lazy lists (`MyPatrolScreen.kt:86`, `PatrolControlScreen.kt:62`, and `PatrolGridAppScaffold.kt:359`).
- Material typography uses `sp`, primary actions have minimum heights above 48 dp, decorative icons are generally removed from the accessibility tree, and the route Canvas has a meaningful content description.
- Empty mission and unit states explain what will happen next instead of presenting unexplained blank space.
- Assignment language explicitly preserves field judgment and distinguishes suggested-route from flexible-area guidance (`PatrolAssignmentSheet.kt:101`).
- The UI avoids staff rankings, automatic misconduct findings, and false precision. Review language asks for human context.
- Release builds block screenshots, hide obscuring overlays on supported Android versions, disable backup, and disable cleartext traffic (`MainActivity.kt:77` and `AndroidManifest.xml:13`).
- Network work is dispatched to `Dispatchers.IO`, mutation IDs are idempotent, and WorkManager resumes secure synchronization after weak connectivity.
- Existing unit, instrumentation, Maestro, pgTAP, Auth/PostgREST, and Locust suites provide a strong test foundation even though the UI matrix is incomplete.

## Remaining-work checklist

This checklist records what was not built or not proven at commit `61342b5`. A box should be checked only after implementation and repeatable verification.

### Core patrol and supervisor workflows

- [ ] Make every mission/attention row select a real mission detail.
- [ ] Let patrol personnel choose the correct assigned mission before starting when more than one is available.
- [ ] Load planned route geometry, priority coordinates/radii, field updates, visits, session state, accuracy, and recorded route for the selected mission.
- [ ] Render planned and recorded paths in geographic context with a safe offline/error fallback.
- [ ] Keep all `needs_review` missions in a dedicated server-backed supervisor queue.
- [ ] Submit `approved`, `needs_context`, and `technically_inconclusive` reviews with notes and conflict handling.
- [ ] Capture meaningful observation and deviation details online and offline.
- [ ] Confirm patrol end and persist `completed`, `relieved`, `cancelled`, or `device_issue` accurately.
- [ ] Make Units scrollable and show mission/status/last-update/exception context.
- [ ] Build the supervisor route-template and priority-management workflow, including map geometry and audit-safe archive behavior.
- [ ] Define whether unit membership/account administration belongs in this mobile app or a managed admin console, then build the approved surface.

### Native quality

- [ ] Replace dead chevrons and duplicate radio semantics with truthful, single-focus controls.
- [ ] Meet 4.5:1 small-text and 3:1 meaningful-icon contrast in both appearances.
- [ ] Add compact, medium, and expanded layouts with navigation bar/rail/drawer selection by window size.
- [ ] Verify portrait, landscape, split-screen, foldable posture, and tablet list-detail behavior.
- [ ] Make sign-in, onboarding, assignment, More, and unit surfaces reachable at 200% font scale with the IME open.
- [ ] Move PatrolGrid copy/plurals to resources and test long-string plus RTL locales.
- [ ] Use subdivision timezone and locale-aware mission formatting.
- [ ] Offer System/Light/Dark appearance without coupling theme to role.
- [ ] Add mission paging and a clearly bounded history experience.
- [ ] Add approved supervisor-led account recovery through the existing official Department channel; do not add a separate support desk.
- [ ] Add last-sync, pending-evidence, cache-age, retry, and persistent failure status.
- [ ] Remove Alerts/Messages placeholders from QA navigation until complete, or implement them end-to-end.

### Verification and rollout evidence

- [ ] Maintain a control inventory covering every visible button, row, tab, sheet action, system Back path, cancel path, and disabled/loading state.
- [ ] Add patrol E2E for priority visits, detailed observation/deviation, end reasons, offline queue, restart, and eventual sync.
- [ ] Add supervisor E2E for mission selection, unit drill-down, planned-vs-recorded map, attention queue, review outcome, conflict, and assignment errors.
- [ ] Add secure-entry E2E for success, wrong password, expired session, no membership, cached identity, sign-out guards, and supervisor-led recovery guidance.
- [ ] Add denied/approximate/revoked location and notification-permission scenarios.
- [ ] Add TalkBack semantics, 200% font, light/dark, locale/RTL, compact/expanded, rotation, process-death, and multi-window checks.
- [ ] Add measured cold-start, frame-time, dense-route, and lower-end physical-device performance gates.
- [ ] Re-run hosted Supabase Auth/RLS/API verification and the staged field pilot after the UI blockers are closed.

## Recommended actions

1. **[P0] `$impeccable harden PatrolGrid supervisor review`** — fix review-state selection, queue every pending mission, and submit auditable human outcomes.
2. **[P0] `$impeccable shape PatrolGrid mission detail`** — establish explicit mission selection and a complete evidence destination.
3. **[P0] `$impeccable shape PatrolGrid geospatial evidence`** — use route GeoJSON and priority coordinates to answer where patrol occurred.
4. **[P1] `$impeccable clarify PatrolGrid field updates`** — capture actual observation/deviation context and accurate patrol-end reasons.
5. **[P1] `$impeccable adapt PatrolGrid`** — implement window-size navigation, scroll/IME resilience, 200% text, tablet, foldable, and multi-window behavior.
6. **[P1] `$impeccable audit PatrolGrid contrast and semantics`** — repair status roles, duplicate radio focus, and dead affordances.
7. **[P1] `$impeccable harden PatrolGrid test matrix`** — prove every consequential control in success, cancel, failure, offline, and restore states.
8. **[P2] `$impeccable adapt PatrolGrid localization`** — resource copy, pluralize counts, use subdivision time, and verify RTL.
9. **[P2] `$impeccable onboard PatrolGrid access recovery`** — add the approved supervisor-led managed-account recovery path without a separate support desk.
10. **[Final] `$impeccable polish PatrolGrid`** — run the final native pass only after P0/P1 workflows are complete and repeatable.

You can ask to run these one at a time, all at once, or in any order. Re-run `$impeccable audit` after fixes to measure the new score rather than editing this baseline retroactively.
