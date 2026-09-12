# DailyBeat end-to-end build report

Date: 2026-09-12
Branch: `claude/end-to-end-ui`
Code head: `7cbf8d6` — the commit every gate result and the QA APK below were produced from.
Branch head: this report's own commit, which changes documentation only.
Baseline: `2cc9784` (handoff commit, on top of released `v3.9.0` = `103f903`)

---

## Outcome

**A reviewable QA build is ready. Six verified prototype-to-production gaps are closed, every gate
that can run on this machine passes, and nothing about the stable v3.9 release was touched.**

| Gate | Result |
|---|---|
| Python policy tests | **36 passed, 0 failed** (was 31 — 5 added) |
| Android JVM unit tests | **233 passed, 0 failed, 0 skipped** (was 205 — 28 added) |
| `lintDebug` | **0 errors, 30 warnings** — warning count unchanged from baseline |
| `assembleDebug` + `assembleDebugAndroidTest` (`.qa.e2eloop`) | **pass** |
| `connectedDebugAndroidTest` (API 34 emulator) | **31 tests, 0 failures, 0 errors, 2 skipped** |

Both skips are self-documenting `assumeTrue` guards for genuinely absent environment, not weakened
assertions — detailed below.

No production tag, release, merge, or push happened. No dependency, AGP, Kotlin or Gradle version
changed. No Room schema changed. The stable `v3.9.0` APK on this machine still hashes to
`30ce22bf2c2a95d84a84b4670cc9a28a82f82a4f0eca40b90dd7fe6ffb5b83e4`, exactly as recorded in
`CLAUDE.md`.

---

## What changed and why

All six gaps came out of the Phase 1 audit in `docs/IMPLEMENTATION_MATRIX.md`. Every one was
**presentation over data the app already computed and then discarded** — which is why none needed a
schema change, a new dependency, or a new permission.

| Commit | Gap | Summary |
|---|---|---|
| `e68aa44` | — | `docs/IMPLEMENTATION_MATRIX.md`: the full prototype-vs-production audit |
| `3dd4511` | G5 | Centre readable content at expanded widths on every shell screen |
| `f8dfe55` | G2 | Stop reporting a chosen privacy pause as "Capture is off" |
| `71dda87` | G4 | Say how current the last GPS fix is, not just how precise |
| `6a69a6a` | G3 | Surface capture gaps on Today and Review instead of dropping them |
| `f1af306` | G1 | List the day's moments on Today |
| `9e45b30` | G6 | Put capture and named places ahead of appearance and cloud in Settings |
| `242279e` | G2 follow-up | Refresh the capture card immediately, not on the next minute tick |
| `723e9fc` | G6 follow-up | Instrumentation: scroll to Appearance now that it is not the first group |
| `7cbf8d6` | G5 follow-up | Constrain the onboarding welcome copy to a readable measure too |

Ten commits, 21 files, **+1263 / −141**.

### The two that matter most

**G2 — a paused capture was reported as a failure.** `SettingsRepository` has had full pause
support since v3.8 and `CaptureController` honours it, but `CaptureHealth.status()` only ever saw
`enabled` and `serviceRunning`. A one-hour privacy pause therefore landed in the same branch as a
disabled toggle and a crashed service: `CaptureHealthLevel.OFF`, *"Capture is off — DailyBeat is
not recording your route"*, in `colorScheme.error`.

So the officer deliberately paused capture for privacy, returned to Today, and was shown an alarm
about a state they had chosen on purpose — one that also implied the pause was permanent, hiding
the single most reassuring fact: that capture comes back by itself, and when. There is now a
`PAUSED` level carrying `resumesAtMs`, rendered in tertiary (never error) with the exact resume
clock time and an inline "Resume capture now".

**G3 — capture gaps were computed and thrown away.** `DayFeedBuilder` has detected gaps since v3.7.
The count reached `TodayUiState` and `InsightsUiState`, and Insights used it only to pick *which*
insight to show. Neither Today nor Review ever told the officer a gap existed, so a day with a
40-minute hole presented its distance and tracked time with exactly the same confidence as a fully
captured day. That is the fake precision `PRODUCT.md` forbids.

### Prototype-to-production decisions

The prototype was treated as authoritative for design intent, the released app for real behaviour.
Several prototype elements were deliberately **not** adopted; each is recorded with its reason in
the matrix. In short: hard-coded mock numbers, a route SVG drawn unconditionally on every surface,
a side-by-side action row that collides at 200% font scale, a toast TalkBack cannot reliably
announce, an unlabelled brand pin in the rail's traversal order, and a web font stack.

Conversely, production was already **ahead** of the prototype on Days, Insights chart
accessibility, Review (hide/restore and reopen are both reversible), Add Moment, and the whole map
subsystem. Those were left alone rather than churned.

### Foundations — verified, not migrated

`dailybeat-design-tokens.json` was generated *from* the released Compose theme, so reconciling it
was a verification step. Every colour, type ramp entry, radius and breakpoint was checked
value-by-value against `Color.kt` / `Type.kt` / `Shape.kt`: **they match**. Production additionally
carries a lifted night set for the six event accents and a `WarningAmber*` attention triple the
token file omits, because the prototype never renders an attention state on a dark surface.
Production is the superset and stays authoritative. **No theme file was edited.**

---

## Exact commands and results

`JAVA_HOME=/opt/homebrew/opt/openjdk@17`, `ANDROID_HOME=$HOME/Library/Android/sdk`.

```bash
python3 -m pytest scripts/tests/ -q
# 36 passed in 0.19s
```

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --stacktrace
# BUILD SUCCESSFUL in 1m 6s
# unit: tests=233 failures=0 errors=0 skipped=0
# lint: 0 errors, 30 warnings
```

```bash
cd android
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop --no-daemon --stacktrace
# BUILD SUCCESSFUL in 4m 8s
# connected: tests=31 failures=0 errors=0 skipped=2
# applicationId      com.dailybeat.app.qa.e2eloop
# test applicationId com.dailybeat.app.qa.e2eloop.test
```

Device for the connected run: `aosp_default_34` AVD, **API 34**, booted headless
(`-no-window -gpu swiftshader_indirect`).

### Tests added (28 JVM + 5 policy)

| File | Covers |
|---|---|
| `capture/CapturePausedStatusTest.kt` | G2. A pause is never `OFF`; an expired deadline falls through to the real state; GPS-off wins over a stale deadline; an expired deadline over a stopped service stays `OFF` rather than being softened into "resuming shortly". |
| `util/FixAgeTest.kt` | G4. Sub-minute is "just now", minutes always round **down** so a fix is never claimed fresher than it is, negative ages from a clock change are clamped. |
| `ui/components/CaptureCoverageTest.kt` | G3. The third branch is the point: a day with nothing captured claims neither "complete" nor "has gaps", because before the first fix "No capture gaps" would be a lie of omission. |
| `ui/today/TodayMomentsTest.kt` | G1. Newest-first, independent of the DAO's `ORDER BY`, stable for same-millisecond ties, nothing dropped. |
| `ui/components/ReadableContentWidthTest.kt` | G5. No-op below 840dp, centred above it, unbounded width passed through rather than clamped. |
| `scripts/tests/test_settings_order.py` | G6. Group order, plus the product constraint asserted **separately** from the exact sequence — the sequence may legitimately change when a group is added; the constraint may not. |

Layout order and Compose layout arithmetic cannot be seen by JVM tests. Rather than leave those
untested, the decision logic was split into pure functions (`readableChildMaxWidth`,
`captureCoverage`, `todayMomentsOrder`, `Formatters.fixAge`) and the Settings order is guarded by a
source-level policy test in the existing `scripts/tests/` suite. This follows the house style set
by `Formatters.RelativeDay`: classify in testable code, word it in string resources.

---

## QA artifact

| Field | Value |
|---|---|
| Path | `outputs/dailybeat-claude-qa-7cbf8d6/DailyBeat-claude-end-to-end-ui-7cbf8d6-QA-debug.apk` |
| Package | `com.dailybeat.app.qa.e2eloop` |
| Version | `versionCode 20`, `versionName 3.9.0` |
| Size | 63,287,127 bytes (60.4 MB) |
| SHA-256 | `40c25527b0f37181c6bc42475a03078847f34348da967a4c83c89e496399a12e` |
| Signing cert | `C=US, O=Android, CN=Android Debug` — SHA-256 `d04106026b7d2ef98f552433b09252179d1930e5e9dd890ac824efcfd01922bc` |

Companion instrumentation APK: `...-QA-androidTest.apk`, package
`com.dailybeat.app.qa.e2eloop.test`, SHA-256
`5ad4ad53b2ee8b1f1045a2d6822dae59baa7c47b8c06f55e8b1f6dc488d125c7`.

Hashes are in `outputs/dailybeat-claude-qa-7cbf8d6/SHA256SUMS.txt`.

### Isolation and signing checks

- The APK installs as **`com.dailybeat.app.qa.e2eloop`**, never `com.dailybeat.app`. Verified with
  `aapt2 dump badging`, and by installing it on the emulator.
- It is signed with the **Android debug key**, not the release identity. The production certificate
  fingerprint `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f` appears nowhere in
  this build; no keystore was created, moved, or read.
- The build script enforces this independently: `build.gradle.kts` requires the debug suffix to
  match `\.qa(\.[a-zA-Z][a-zA-Z0-9_]*)*`, and `verifyDisposableTestTarget` makes
  `connectedDebugAndroidTest` and `installDebugAndroidTest` fail unless the suffix is exactly
  `.qa.e2eloop`. Production package data cannot be reached by these tasks even by mistake.
- The stable `outputs/DailyBeat-v3.9.0.apk` was not overwritten; it still hashes to
  `30ce22bf…b5b83e4`. QA artifacts went to a new, separately named directory.

---

## Device evidence

### Automated — API 34 emulator, full suite: 31 tests, 0 failures, 2 skipped

Skips, both `assumeTrue` guards that the tests declare themselves:

- `CloudBackupLiveTest.phoneBackupAndRestoreRoundTrip` — no live Supabase credentials on this
  machine. Skips rather than passing vacuously.
- `CaptureLifecycleTest.openingTheAppReArmsPassiveCapture` — the `aosp_default_34` AVD is an AOSP
  image with no Google Play Services, so there is no fused location provider. The test says so in
  its own assumption message and defers to the physical-phone gate. **Note: this test did pass** on
  an earlier run against a Play-Services emulator, so it is covered, just not by the final run.

### Two real failures were found and dealt with

**1. `appearanceSelectorChangesThemeAndSurvivesActivityRecreation` — a genuine consequence of G6,
now fixed.** The test clicked `theme_dark` without scrolling, which only worked because Appearance
used to be the first group on Settings. After G6 moved capture and named places above it, on a
393dp phone the theme selector sits below the fold: the heading is still composed so
`assertIsDisplayed` passes, but the click lands outside the viewport and the 5 s `waitUntil` times
out.

I did not guess at this. I installed the QA APK and screenshotted Settings
(`evidence/03-settings-reordered-capture-places-first.png`), which shows the "Appearance" heading
clipped at the very bottom edge with the selector below it. Fixed in `723e9fc` by scrolling to the
node first — which is how every other Settings test in that file already reaches its target. The
behaviour under test (theme selection and its survival across activity recreation) is unchanged and
still fully asserted; only the navigation to the control was adapted to the new, intentional order.

**2. `syntheticDayCanBeLoadedRepeatedlyWithoutDuplicatingRecords` — emulator timing, no change
made.** Failed once at a 10 s `waitUntil` polling the audit log, during a run where the whole suite
took 16 minutes on a software-GPU AVD. It passed on re-run with no code change, and passes in the
final suite. Nothing in this work touches `SyntheticDayGenerator` or `CaptureAuditLog`. Recorded
here rather than quietly forgotten.

### An API-37 emulator is not usable for this project

A run against a `Medium_Phone` AVD reporting `ro.build.version.sdk=37` (Android 17) failed 27 of 31
tests, all with `java.lang.NoSuchMethodException: android.hardware.input.InputManager…` — the known
Espresso break on new API levels. The project pins `androidx.test:runner:1.6.1` /
`androidx.test.ext:junit:1.2.1` against `compileSdk 35`; those predate the `InputManager` change, so
every UI-driving test dies in the harness before touching app code.

This is a toolchain mismatch, not a regression. Fixing it means upgrading `androidx.test`, which
`CLAUDE.md` constraint 10 forbids in this build and which belongs in a separate compatibility task.
The gate was not weakened, skipped or deleted — it was run, and it is reported as unusable on that
API level with the reason stated. **Run instrumentation on API 34.**

### Manual visual verification

Captured by installing the QA APK on the emulator. Files in
`outputs/dailybeat-claude-qa-7cbf8d6/evidence/`:

| Screenshot | Shows |
|---|---|
| `01-onboarding-welcome.png` | Onboarding, 393dp phone |
| `02-today-capture-off-empty-state.png` | Today with capture off — honest empty map state, "Capture is off" card, zeroed metrics, disabled Review action |
| `03-settings-reordered-capture-places-first.png` | G6 on a phone: Capture → Named places, Appearance clipped at the fold (the evidence behind the test fix above) |
| `04-tablet-onboarding-BEFORE-full-width.png` | The G5 miss: onboarding copy running the full 1066dp as one line |
| `05-tablet-onboarding-AFTER-readable-measure.png` | Same screen after `7cbf8d6` |
| `06-tablet-today-rail-centred-moments.png` | **G5 + G1 at 1066dp**: navigation rail present, content capped at 840dp and centred, and the new "Today's moments · Local to this phone" section with its empty-state copy |
| `07-tablet-settings-order-light.png` | **G6 at tablet width, light**: Capture → Named places → Appearance → Officer name → Cloud backup → Cloud AI |
| `08-tablet-settings-order-dark.png` | Same in dark theme — ordering, rail and centring all hold |

Tablet width was produced with `wm size 1600x2560` / `wm density 240` = 1066dp, then reset.

The G5 miss on onboarding was found precisely *because* I looked at the rendered screen rather than
trusting the grep that produced the matrix. That is worth noting: the audit's own coverage check
said "onboarding: 0 `widthIn`" and I still scoped the fix to the shell screens.

### Still not covered

| Gate | Why |
|---|---|
| Live Supabase backup round trip | No credentials on this machine. |
| Physical-device run via `scripts/mac_phone_e2e.sh` | No phone attached; not attempted. |
| 200% font scaling | Not exercised. The Today action stack was deliberately kept vertical for this reason, but that reasoning is untested. |
| Landscape / short viewport | Not exercised. |
| TalkBack traversal, headings, and state announcements | Needs manual operation with the screen reader on. New `semantics { heading() }` on the Today moments header is unverified by ear. |
| 48dp target sweep | Not measured. The known Insights bar-width deviation below is pre-existing. |
| System Back from sheets / Review / full map | Covered indirectly by `MainNavigationTest`, not audited deliberately. |

I did not update any golden screenshot, relax any assertion, or delete any test to make this report
look cleaner.

---

## Known deviations recorded rather than hidden

- **Insights chart touch targets.** Each of the 7 bars has a full 132dp-tall touch target but only
  ~40dp of width on a 360dp phone, below the 48dp minimum. Widening would mean dropping to 6 bars
  or removing surface padding, both of which cost more than they return; the chart is fully
  operable through its accessibility node and the text readout above it. Pre-existing, not
  introduced here; recorded in the matrix as an accepted deviation.
- **30 lint warnings**, unchanged in count from baseline. Includes a deprecated
  `Icons.Outlined.Assignment` and unused-parameter warnings that predate this work.

---

## Risks

- The visual matrix above is the real gap: 200% font scale, landscape and TalkBack are unverified.
  G5's behaviour is now seen at tablet width, but large-text reflow is not.
- `readableContentWidth()` is a custom layout modifier. Its arithmetic is unit-tested and it is a
  no-op below 840dp (so no phone layout can change), and it has now been observed working inside
  the real `NavigationRail` row — but only at one width, on one device.
- `CaptureHealthLevel.PAUSED` is an additive enum constant and `status()` gained a defaulted
  parameter, so every existing caller and test compiled unchanged. Nothing persists the enum, so
  there is no migration surface.
- The G6 reorder changed where controls sit. One instrumentation test depended on the old position
  and was caught; a manual pass should confirm nothing else assumed Appearance was at the top.

---

## Recommended next action

1. Install the QA APK and walk the six changes by hand, prioritising **200% font scale, landscape,
   and TalkBack** — the three things nothing here has verified.
2. Re-run `connectedDebugAndroidTest` on a **Play-Services API 34** image to also cover
   `openingTheAppReArmsPassiveCapture`, which the AOSP image skips.
3. Only then decide about a release. This branch is deliberately left unmerged, unpushed and
   untagged.

---

## Confirmations

- Working tree clean; all work committed locally on `claude/end-to-end-ui`.
- Nothing pushed, merged, tagged, or released.
- Production signing identity untouched; no keystore created, read, or substituted.
- Production package `com.dailybeat.app` never installed, launched, or targeted by any test.
- Stable `v3.9.0` artifacts byte-identical.
- No Dependabot PR merged; no dependency, AGP, Kotlin or Gradle version changed.
- No Room schema change, therefore no migration risk.
- No analytics, telemetry, or new logging added. No credential, coordinate, prompt, or diary
  content is written to any log by this work.
