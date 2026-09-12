# DailyBeat implementation matrix — Penpot v4 → production Compose

Audit date: 2026-09-12
Branch: `claude/end-to-end-ui`
Baseline commit: `2cc9784` (on top of released `v3.9.0` / `103f903`)

## How to read this

The phone-only Penpot v4 architecture is authoritative for **design intent, hierarchy,
phone composition, and interaction direction**. The released Compose app is authoritative for
**real behavior, privacy, storage, and Android semantics**.

Every row is classified as one of:

- **Matched** — production already satisfies the prototype's intent.
- **Production wins** — production deliberately differs and is better; no change. The prototype is a
  static mock with invented data, so anything it "shows" that the real app cannot honestly know is
  a mock artifact, not a requirement.
- **Gap** — production is genuinely missing something the prototype and `CLAUDE.md` both call for.

Gaps carry the exact change and the test that proves it.

---

## Summary

| # | Area | Status | Severity |
|---|---|---|---|
| G1 | Today — chronological moments timeline | **Gap** | High |
| G2 | Capture — paused is indistinguishable from off | **Gap** | High |
| G3 | Today / Review — capture gaps never surfaced | **Gap** | High |
| G4 | Today — recent-fix age never shown | **Gap** | Medium |
| G5 | Phone landscape — content not width-constrained on 5 of 6 screens | **Gap** | Medium |
| G6 | Settings — group order contradicts the product brief | **Gap** | Medium |
| — | Foundations (color/type/shape/motion tokens) | Matched | — |
| — | Phone navigation (bottom bar at every width) | **Gap, fixed for v4.0** | High |
| — | Days, Insights, Review, Add Moment, Diary, Map | Matched or production wins | — |

Six gaps, all of them real and all of them backed by data the app already computes and then
discards. No gap required a schema change, a new dependency, or a new permission.

---

## Foundations — `ui/theme/`

| Prototype intent | Production | Owner | Status |
|---|---|---|---|
| Navy `#0B2D5B`, navy-soft `#294E78`, signal `#FFD60A` | Identical values | `Color.kt` | Matched |
| Light surfaces `#F8FAFC` / `#FFFFFF` / `#EEF3F8`, ink `#0B1B33`, muted `#475569`, outline `#7C8DA3` | Identical | `Color.kt` | Matched |
| Dark surfaces `#081A2E` / `#10263D` / `#183149`, ink `#F2F6FA`, muted `#B7C5D4`, primary `#B8D4FF` | Identical | `Color.kt` | Matched |
| Six event accents (manual/voice/gps/call/visit/moment) | Identical, **plus** a second lifted night set the token file does not have | `Color.kt`, `EventCard.kt` | Production wins |
| Roboto / system sans | `FontFamily.SansSerif` — resolves to Roboto on Android and honours a user's font substitution | `Type.kt` | Matched |
| Type ramp 28/22/18/16/16/14/14/11 | Identical sizes and weights | `Type.kt` | Matched |
| Radii 8/16/20 | `Shapes` 8/12/14/16/20 — a superset | `Shape.kt` | Matched |
| Theme follows an explicit Light/Dark/System choice, not just the system | `LocalDarkTheme` + `ThemePreference`; status/nav bar icon polarity follows the choice | `Theme.kt` | Production wins |

**No change needed.** `dailybeat-design-tokens.json` was generated *from* the released Compose
theme, so reconciling it is a verification step, not a migration. Verified value-by-value above.

The token file omits the night event accents and the `WarningAmber*` attention triple that
production carries. Those exist because the prototype never renders an attention state on a dark
surface; production does. Production is the superset and stays authoritative.

---

## Phone shell — `ui/DailyBeatAppScaffold.kt`

| Prototype intent | Production | Status |
|---|---|---|
| Bottom navigation, 4 destinations | `NavigationBar` at every phone width | Matched in v4.0 |
| No tablet navigation mode | Width-triggered `NavigationRail` removed | Matched in v4.0 |
| Bar hidden on Review and full Map | `showTopLevelNavigation` excludes `MAP` and `REVIEW` | Matched |
| Selected destination indicated | Filled vs outlined icon **and** indicator **and** label — not colour alone | Matched |
| `screen-inner { width: min(100%, 840px); margin: 0 auto }` — centred readable content at every width | **Only `TodayScreen` applied `widthIn(max = 840.dp)`** | **G5 — Gap, fixed** |
| Stable reach and muscle memory in portrait/landscape | Same four bottom destinations at every width | Matched in v4.0 |

### G5 — content is not width-constrained outside Today

- **Observed:** `grep -c widthIn` → `TodayScreen` 2, and `FeedScreen` / `InsightsScreen` /
  `SettingsScreen` / `ReviewDayScreen` / `DiaryScreen` / `JourneyMapScreen` / `OnboardingScreen`
  all **0**.
- **Effect:** on a landscape or externally resized phone window, Days/Insights/Settings/Review can stretch
  their text to the full width. Line lengths run far past a readable measure, and
  `SpaceBetween` stat rows fling their three numbers to opposite edges of the screen.
- **Why it matters:** phone landscape still needs readable line lengths even though the navigation
  model must stay unchanged.
- **Change:** add one shared `Modifier.readableContentWidth()` in `ui/components/` (max 840dp,
  centred) and apply it to every top-level screen's scroll container, replacing Today's inline
  `widthIn`. One definition, so the constant cannot drift per screen.
- **Test:** `ReadableContentWidthTest` asserting the modifier caps at 840dp and is a no-op below it.

---

## Today — `ui/today/TodayScreen.kt`, `TodayViewModel.kt`

| Prototype element | Production | Status |
|---|---|---|
| `Today` + date subtitle | `DailyBeatScreenHeader` with `Formatters.dayHeading(LocalDate.now())`, `semantics { heading() }` | Matched |
| Route map proof | `JourneyRoutePreview` when `beat.hasRoute`, else `WaitingForRouteCard` differentiating waiting vs tracking-off | Production wins — prototype's SVG route is drawn unconditionally; production refuses to draw a route it does not have |
| Capture card: pulse + title + subtitle | `CaptureOverview` with a 4-state health model (HEALTHY / WAITING / DEGRADED / OFF) driven by `CaptureHealthStore` | Production wins on state modelling — but see G2 and G4 |
| Capture card: "Last reliable fix 2 minutes ago" | Shows accuracy in metres; **the computed fix age is discarded** | **G4 — Gap** |
| Capture card: inline Pause/Resume | Pause lives only in Settings; a paused capture renders as "Capture is off" | **G2 — Gap** |
| Meta row: GPS reliable · Cloud ready | `StatusStrip` → two `StatusChip`s, icon + text (not colour alone) | Matched |
| Beat summary: title, subtitle, state label, 3 metrics | `BeatSummary` with real distance/tracked/stops and an explicit "estimated" note | Production wins — prototype hard-codes `12.4 km`; production marks estimated distance as estimated |
| Primary `Review my day` | `PrimaryButton`, `testTag("review_day")`, disabled when there is genuinely nothing to review | Matched |
| Secondary `Add moment` + `Open diary` | Both present, stacked vertically rather than side-by-side | Production wins — a 2-up row of 48dp buttons collides at 200% font scale; the prototype never scales text |
| **`Today's moments` timeline — "Local to this phone"** | **Absent.** `todayEvents` is collected in the ViewModel and never rendered | **G1 — Gap** |
| Capture gap honesty | `captureGapCount` reaches `TodayUiState` and is never rendered | **G3 — Gap** |

### G1 — Today has no chronological moments timeline

- **Observed:** `TodayViewModel.todayEvents` is a `StateFlow` built from
  `repository.observeTodayEvents()`. `TodayScreen` never reads it. The only place an `Event` is ever
  rendered is `DiaryScreen.kt:177` (`EventCard`), one navigation hop away.
- **Effect:** the officer saves a moment from Today's sheet, sees a transient "Moment saved" toast,
  and Today looks exactly as it did before. The thing they just recorded is invisible on the screen
  they recorded it from. The prototype's third-largest block on Today is this list.
- **Why it matters:** `CLAUDE.md` Phase 3 Today — "Date/purpose, honest map proof, consolidated
  capture overview, one Beat summary, primary review action, secondary Add Moment/Diary actions,
  *and chronological moments*." Also `PRODUCT.md` design principle 2, "the day is the product".
- **Change:** render a `TodayMomentsSection` from the already-collected `todayEvents`, newest first,
  reusing the existing `EventCard` accents so the visual language matches Diary. Include the
  prototype's "Local to this phone" provenance line, an empty state, and a heading semantic.
- **Test:** `TodayMomentsTest` — ordering is newest-first, the section is absent when there are no
  events, and provenance copy is present.

### G3 — capture gaps are computed and then dropped

- **Observed:** `DayFeedBuilder` detects gaps (`CAPTURE_GAP_MS = 10 min`) and reports
  `captureGapCount`. It reaches `TodayUiState.captureGapCount` (`TodayViewModel.kt:163`) and
  `InsightsUiState.captureGapCount`. Insights uses it only to *choose* which insight to show.
  **Neither Today nor Review ever tells the officer a gap exists.**
- **Effect:** a day with a 40-minute hole in it presents its distance and tracked-time numbers with
  exactly the same confidence as a fully-captured day. That is the "fake precision" the product
  doctrine forbids, and the prototype's Review screen explicitly claims "3 stops · no capture gaps"
  — a claim production cannot currently make *or* contradict.
- **Why it matters:** `CLAUDE.md` Phase 3 Today ("capture gaps ... factual") and Phase 3 Review
  ("Show uncertainty and capture gaps honestly"); `PRODUCT.md` principle 1, "trust before delight".
- **Change:** surface gap count as an honest line on Today's capture card and on Review's summary —
  stating the gap count when there is one and stating "no capture gaps" when there is none, so the
  absence of a warning is itself a positive claim rather than silence.
- **Test:** `CaptureGapPresentationTest` covering zero / one / many gaps and correct pluralisation.

### G4 — recent-fix age is computed and never shown

- **Observed:** `CaptureHealth.status()` computes `lastPointAgeMs`. `CaptureOverview` renders
  `capture_accuracy` ("Last point accurate to about N m") and ignores the age.
- **Effect:** accuracy answers "how precise was the last point", not "is this current". A 6-metre
  fix from three hours ago reads identically to a 6-metre fix from one minute ago. DEGRADED is
  entered at 5 minutes but the card still will not say *how* stale.
- **Why it matters:** `CLAUDE.md` Phase 3 Today — "Keep *recent-fix age*, GPS reliability, capture
  gaps, and cloud readiness factual". The prototype leads this card with "Last reliable fix 2
  minutes ago".
- **Change:** format `lastPointAgeMs` into the capture detail line for HEALTHY and DEGRADED, keeping
  accuracy as secondary detail.
- **Test:** `CaptureAgeFormatTest` over just-now / minutes / hours boundaries.

---

## Capture — `capture/CaptureHealthStore.kt`, `CaptureController.kt`

### G2 — a paused capture is reported as "Capture is off"

- **Observed:** `SettingsRepository` has full pause support (`pauseCaptureUntil`,
  `capturePausedUntilMs`, `isCapturePaused`) and `CaptureController` honours it by not starting
  `LocationService`. But `CaptureHealth.status(now, enabled)` only knows `enabled` and
  `serviceRunning`, so a privacy pause falls into the same branch as a disabled toggle and a crashed
  service: `CaptureHealthLevel.OFF`, reason `"Tracking is off"`.
- **Effect on Today:** the officer taps "Pause capture for one hour" in Settings for a deliberate
  privacy reason, returns to Today, and is told **"Capture is off — DailyBeat is not recording your
  route"** in `colorScheme.error`. That is an alarm about a state the officer chose on purpose. It
  also implies the pause is permanent, hiding the single most reassuring fact — that capture comes
  back by itself, and when.
- **Why it matters:** `CLAUDE.md` Phase 3 Today requires "paused" as one of the states that "must
  all have recovery copy", and Phase 4 lists "one-hour privacy pause → process recreation/reboot
  handling → automatic resume" as an end-to-end path. Reporting a chosen pause as a failure is
  exactly the "never communicate ... by color alone" / honest-state bar inverted.
- **Change:** add `CaptureHealthLevel.PAUSED` carrying `resumesAtMs`, thread the real
  `capturePausedUntilMs` from `SettingsRepository` into the status computation, and render a
  tertiary (not error) capture card naming the exact resume clock time with an inline
  "Resume capture now" action. Pause remains settable from Settings; Today gains the honest readout
  and the one-tap undo.
- **Test:** `CapturePausedStatusTest` — paused-with-future-deadline yields `PAUSED` with the right
  resume time; an expired deadline falls back to the real underlying state; disabled-GPS still
  yields `OFF` and is never mislabelled as paused.
- **Note:** existing `CapturePauseTest` and `CaptureHealthTest` must keep passing unchanged; the new
  level is additive.

---

## Days — `ui/feed/FeedScreen.kt`, `DayFeedItem.kt`, `DayRouteThumbnail.kt`

| Prototype element | Production | Status |
|---|---|---|
| Map-led day record, thumbnail left | `DayRouteThumbnail` rendered only when `day.hasRoute` | Production wins — the prototype's thumbnail is a CSS pseudo-element that draws a fake route on every card including days with no data |
| Date, Beat title, place summary | `relativeDayLabel` (Today/Yesterday/N days ago), `day.title`, `Formatters.dayHeading` | Production wins |
| Distance · stops · status facts | `StatBlock` ×3 with estimated-distance marking | Matched |
| Truthful review status | `DayStateBadge` shows a badge **only** for `complete` and `needs_review`; ordinary captured days carry none | Production wins — deliberate, documented in-code, so Days does not nag review on every past day |
| Tap a day opens its journey map; review is explicit | `onOpenDay` → `Routes.map(dateKey)`; review is reached from Today | Matched — required by `CLAUDE.md` Phase 3 Days and already correct |
| — | Per-stay rows, expand/collapse persisted across scroll+rotation, inline place naming, weekly roll-up, week export | Production wins — no prototype equivalent |
| Centred content at expanded width | Absent | Covered by **G5** |

No Days-specific gap. This screen is materially ahead of the prototype.

## Insights — `ui/insights/InsightsScreen.kt`

| Prototype element | Production | Status |
|---|---|---|
| Lead insight sentence + supporting body | `actionable_insight` surface with title, body **and a concrete action button** | Production wins |
| 7-day distance bar chart | `WeeklyBars`, oldest→newest | Matched |
| Chart selection | `selectable` with `Role.Button`, per-bar `contentDescription` carrying day + distance, and a text readout above the chart | Production wins — the prototype's bars are inert `div`s with no selection at all |
| Value not conveyed by colour alone | Selected bar differs by **width (26 vs 22dp), label weight, label colour, and the readout text** | Matched |
| Recurring places with visit counts | Present, plus an empty state | Matched |
| 28-day private framing | `insights_privacy_note` | Matched |
| Centred content at expanded width | Absent | Covered by **G5** |

**One sub-48dp observation, not filed as a gap:** each bar's touch target is the full 132dp column
height but only ~40dp wide on a 360dp phone (7 bars sharing width after 40dp screen + 36dp surface
padding). Widening to 48dp would require either dropping to 6 bars or removing the surface padding,
both of which cost more than they return — and the chart is fully operable via its accessibility
node and the readout. Recorded here as a known, accepted deviation rather than silently ignored.

## Settings — `ui/settings/SettingsScreen.kt`

| Prototype / brief requirement | Production | Status |
|---|---|---|
| System/Light/Dark selector | `SingleChoiceSegmentedButtonRow`, persisted | Matched |
| GPS breadcrumbs toggle | `ToggleRow` | Matched |
| Pause capture, auto-resume after one hour | Present, with resume-now action | Matched (see G2 for the Today-side readout) |
| Named places, private zones | Full CRUD, current-location capture, private-zone flag, delete confirmation, suggestions | Production wins |
| Cloud AI configured, "location points never sent" | Present | Matched |
| Cloud backup | Present | Matched |
| Technical provider/base-URL detail kept subordinate | Cloud AI group sits below identity and backup; keys are masked | Matched |
| **Capture/privacy and named places ordered before appearance, Cloud AI, and backup** | Order is Appearance → Capture → Officer → Backup → Cloud AI → QA → **Named places (last)** | **G6 — Gap** |

### G6 — settings group order contradicts the brief

- **Observed group order** (`grep -n 'SettingsGroup(title'`): 153 Appearance, 188 Capture, 249
  Officer name, 272 Cloud backup, 373 Cloud AI, 467 QA, **488 Named places**.
- **Required** (`CLAUDE.md` Phase 3 Settings): "Order capture/privacy and named places before
  appearance, Cloud AI, and backup."
- **Effect:** the two groups that carry the app's privacy contract — capture control and named
  places / private zones — are respectively second and *dead last*, below API-key configuration and
  a debug-only QA group. A private zone is the mechanism that keeps a home address out of every
  cloud report and export; it should not sit below a base-URL field. Meanwhile Appearance, a pure
  cosmetic preference, opens the screen.
- **Change:** reorder to Capture → Named places (+ place list) → Identity → Appearance → Cloud AI →
  Cloud backup → QA. Move only; no behavioural edits to any group's contents.
- **Test:** `SettingsOrderTest` asserting the group sequence, so the order cannot silently regress.

## Review my day — `ui/review/ReviewDayScreen.kt`

| Prototype element | Production | Status |
|---|---|---|
| Back affordance + screen title | `IconButton` with `review_back` description | Matched |
| Day heading + state badge | Present | Matched |
| Per-stop rows with time range | `ReviewVisitRow`, `Formatters.clockRange` | Matched |
| Rename a stop | Dialog, reversible | Matched |
| Hide / restore a stop | Toggle, **with an explicit "Hidden" text label** so it is not a tint-only difference | Production wins |
| Beat naming | `beat_title` field + save | Matched |
| Complete / reopen the Beat | Both directions present — completion is reversible | Production wins — the prototype only completes |
| Keyboard handling | `imePadding()` | Matched |
| **"3 stops · no capture gaps" honesty line** | No gap reporting anywhere on Review | Covered by **G3** |
| Centred content at expanded width | Absent | Covered by **G5** |

Rename/hide/restore never delete raw captured history — `setVisitHidden` flips a flag, and the
breadcrumb table is untouched. Verified against `ReviewDayViewModel` and preserved.

## Add Moment / Diary — `ui/today/TodayScreen.kt` sheet, `ui/diary/DiaryScreen.kt`

| Prototype element | Production | Status |
|---|---|---|
| Focused bottom sheet | `ModalBottomSheet` | Matched |
| Free-text note | `OutlinedTextField`, `testTag("moment_note")` | Matched |
| Survives rotation / process recreation | `rememberSaveable` for both the draft text and sheet visibility | Matched |
| Save locally first | `repository.addManualEvent(text)` writes to Room before any cloud call | Matched |
| — | Voice note capture and "mark significant moment" | Production wins — no prototype equivalent |
| — | Cloud report action shown only when `cloudBrainReady` | Production wins |
| Toast confirmation | Inline dismissible `FeedbackMessage` | Production wins — a Compose toast cannot be read reliably by TalkBack; an inline message stays on screen |
| Saved moment appears on Today | **Does not** | Covered by **G1** |

Intentional diary clearing is preserved (`DiaryClearingTest`); not touched.

## Map evidence — `ui/map/`, `ui/components/JourneyMap*`

Production is far beyond the prototype here: real MapLibre rendering, route replay with
play/pause, offline and loading states, a rasterizer for thumbnails, and gap-aware route drawing
(`RoutePoint.startsAfterGap`, `drawsRoute`) so a gap is never bridged by an invented straight line.
The prototype's map is a decorative SVG. **Production wins; no change.**

The one thing the prototype does that production must never copy: it draws a continuous route on
every surface unconditionally. Production's refusal to do so is the "map is proof" rule.

---

## Explicitly rejected prototype elements

| Prototype element | Why production does not adopt it |
|---|---|
| Hard-coded `12.4 km`, `7h 18m`, `3` stops, `18 visits` | Mock data. Production derives every number from captured state and marks estimates. |
| Route SVG drawn on Today and on every Days card | Fabricates a connected route. Violates "the map is proof". |
| `● Ready to review` / `● Reliable` dot-plus-text state labels | Production's badges already carry text; adding a coloured dot would be decoration, and the dot's colour is the only thing distinguishing the two prototype states. |
| Side-by-side `Add moment` / `Open diary` row | Collides at 200% font scaling. |
| `DB` brand pin on the rail | Unlabelled non-target in the rail's traversal order. |
| Transient toast | Not reliably announced by TalkBack. |
| Web font stack (`Roboto, system-ui, sans-serif`) | Android system sans already resolves correctly and honours user font substitution. Bundling a web font is forbidden by the brief. |

---

## Not in scope for this build

- Dependency, AGP, Kotlin, or Gradle upgrades, and open Dependabot PRs (`CLAUDE.md` constraint 10).
- Room schema changes — none of the six gaps needs one; every gap is presentation over data the app
  already computes.
- Figma round-trip (workspace quota exhausted; documented in `DAILYBEAT_DESIGN_ALTERNATIVE.md`).
- Any production release, tag, merge, or push.


---

## Verification status — updated 2026-09-12, head `7cbf8d6`

All six gaps are implemented and gated. Full evidence is in `docs/CLAUDE_BUILD_REPORT.md`; this
section records only what the audit above got wrong or left incomplete.

| Gap | Unit / policy test | Seen rendered on a device |
|---|---|---|
| G1 Today moments | `TodayMomentsTest` | Yes — retained from the earlier wide-window evidence and covered on phone by instrumentation |
| G2 paused capture | `CapturePausedStatusTest` | Not directly (needs a live pause); `CaptureLifecycleTest.captureStaysOffDuringAPrivacyPause` passes |
| G3 capture gaps | `CaptureCoverageTest` | Not directly (needs a day with a real gap) |
| G4 fix age | `FixAgeTest` | Not directly (needs a live GPS fix) |
| G5 readable width | `ReadableContentWidthTest` | Yes — wide-window evidence; v4 keeps the measure but removes the rail |
| G6 settings order | `scripts/tests/test_settings_order.py` | Yes — `evidence/07`, `08` |

### Two things this audit missed

**G5 was scoped too narrowly.** The coverage check above correctly reported `OnboardingScreen: 0`
`widthIn` occurrences, and the fix was still scoped to the six screens inside the navigation shell.
Onboarding has the same readable-measure problem and is the first screen a new user sees; at 1066dp
its welcome paragraph ran the full width as a single line. Fixed in `7cbf8d6`. The grep that built
this matrix found it; the reading of that grep did not. Compare
`evidence/04-tablet-onboarding-BEFORE-full-width.png` with `05-…-AFTER-readable-measure.png`.

`JourneyMapScreen` also has no width constraint and was likewise not changed — deliberately, this
time: it is a full-bleed map surface where a capped measure would be wrong.

**G6 had an instrumentation consequence the audit did not predict.** Moving Appearance from first
to third group pushed the theme selector below the fold on a 393dp phone, breaking
`MainNavigationTest.appearanceSelectorChangesThemeAndSurvivesActivityRecreation`, which clicked
`theme_dark` without scrolling. Fixed in `723e9fc` by scrolling first, matching every other Settings
test in that file. The reorder itself is correct and unchanged; only the test's navigation was
adapted. Confirmed by screenshot, not by assumption — `evidence/03-…` shows the clipped heading.

### Environment limits found while verifying

- Instrumentation must run on **API 34**. An API-37 (Android 17) emulator fails 27 of 31 tests in
  the Espresso harness (`NoSuchMethodException: android.hardware.input.InputManager…`) because the
  project pins `androidx.test:runner:1.6.1`. Upgrading is out of scope per constraint 10.
- An AOSP image skips `CaptureLifecycleTest.openingTheAppReArmsPassiveCapture` (no Play Services,
  so no fused location provider). Use a Play-Services API 34 image to cover it.
