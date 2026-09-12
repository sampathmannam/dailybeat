# DailyBeat end-to-end build report

Date: 2026-09-12
Branch: `claude/end-to-end-ui`
Head commit: `242279e`
Baseline: `2cc9784` (handoff commit, on top of released `v3.9.0` = `103f903`)

---

## Outcome

**A reviewable QA build is ready. Six verified prototype-to-production gaps are closed, every safe
local gate passes, and nothing about the stable v3.9 release was touched.**

- Python policy tests: **36 passed, 0 failed** (was 31 — 5 added).
- Android JVM unit tests: **233 passed, 0 failed, 0 skipped** (was 205 — 28 added).
- `lintDebug`: **0 errors, 30 warnings** — unchanged from the baseline.
- `assembleDebug` + `assembleDebugAndroidTest` under the isolated `.qa.e2eloop` package: **pass**.
- Instrumentation on a device: **run, with one unresolved failure and one environment limitation.
  See "Device evidence" below — this is the one place where evidence is incomplete.**

No production tag, release, merge, or push happened. No dependency, AGP, Kotlin or Gradle version
changed. No Room schema changed. The stable `v3.9.0` APK on this machine still hashes to
`30ce22bf2c2a95d84a84b4670cc9a28a82f82a4f0eca40b90dd7fe6ffb5b83e4`, exactly as recorded in
`CLAUDE.md`.

---

## What changed and why

All six gaps came out of the Phase 1 audit in `docs/IMPLEMENTATION_MATRIX.md`. Every one of them
turned out to be **presentation over data the app already computed and then discarded** — which is
why none needed a schema change, a new dependency, or a new permission.

| Commit | Gap | One-line summary |
|---|---|---|
| `e68aa44` | — | `docs/IMPLEMENTATION_MATRIX.md`: the full prototype-vs-production audit |
| `3dd4511` | G5 | Centre readable content at expanded widths on every screen, not just Today |
| `f8dfe55` | G2 | Stop reporting a chosen privacy pause as "Capture is off" |
| `71dda87` | G4 | Say how current the last GPS fix is, not just how precise |
| `6a69a6a` | G3 | Surface capture gaps on Today and Review instead of dropping them |
| `f1af306` | G1 | List the day's moments on Today |
| `9e45b30` | G6 | Put capture and named places ahead of appearance and cloud in Settings |
| `242279e` | G2 follow-up | Refresh the capture card immediately, not on the next minute tick |

Eight commits, 20 files, **+1218 / −136**.

### The two that matter most

**G2 — a paused capture was reported as a failure.** `SettingsRepository` has had full pause
support since v3.8 and `CaptureController` honours it, but `CaptureHealth.status()` only ever saw
`enabled` and `serviceRunning`. A one-hour privacy pause therefore landed in the same branch as a
disabled toggle and a crashed service: `CaptureHealthLevel.OFF`, *"Capture is off — DailyBeat is
not recording your route"*, in `colorScheme.error`.

So the officer deliberately paused capture for privacy, returned to Today, and was shown an alarm
about a state they had chosen on purpose — one that also implied the pause was permanent, hiding
the single most reassuring fact: that capture comes back by itself, and when. Now there is a
`PAUSED` level carrying `resumesAtMs`, rendered in tertiary (never error) with the exact resume
clock time and an inline "Resume capture now".

**G3 — capture gaps were computed and thrown away.** `DayFeedBuilder` has detected gaps since v3.7.
The count reached `TodayUiState` and `InsightsUiState`, and Insights used it only to pick *which*
insight to show. Neither Today nor Review ever told the officer a gap existed, so a day with a
40-minute hole presented its distance and tracked time with exactly the same confidence as a fully
captured day. That is the fake precision `PRODUCT.md` forbids.

### Prototype-to-production decisions

The prototype was treated as authoritative for design intent and the released app as authoritative
for real behaviour, as instructed. Several prototype elements were deliberately **not** adopted;
each is recorded with its reason in the matrix. The short version:

- Hard-coded mock numbers (`12.4 km`, `18 visits`) — production derives every figure and marks
  estimates.
- A route SVG drawn unconditionally on Today and on every Days card — production refuses to draw a
  route it does not have. "The map is proof."
- A side-by-side Add moment / Open diary row — collides at 200% font scale.
- A transient toast — not reliably announced by TalkBack; production keeps an inline dismissible
  message.
- A `DB` brand pin on the navigation rail — an unlabelled non-target in the rail's traversal order.
- A web font stack — Android system sans already resolves to Roboto and honours user substitution.

Conversely, production was already **ahead** of the prototype on Days, Insights chart
accessibility, Review (hide/restore and reopen are both reversible), Add Moment, and the whole map
subsystem. Those were left alone rather than churned, per the brief's instruction not to
reimplement what the real app already does better.

### Foundations — verified, not migrated

`dailybeat-design-tokens.json` was generated *from* the released Compose theme, so reconciling it
was a verification step. Every colour, type ramp entry, radius and breakpoint was checked
value-by-value against `Color.kt` / `Type.kt` / `Shape.kt`: **they match**. Production additionally
carries a lifted night set for the six event accents and a `WarningAmber*` attention triple that
the token file omits, because the prototype never renders an attention state on a dark surface.
Production is the superset and stays authoritative. No theme file was edited.

---

## Exact commands and results

Run from the repository root unless noted. `JAVA_HOME=/opt/homebrew/opt/openjdk@17`,
`ANDROID_HOME=$HOME/Library/Android/sdk`.

```bash
python3 -m pytest scripts/tests/ -q
# 36 passed in 0.20s
```

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --stacktrace
# BUILD SUCCESSFUL in 2m 27s
# unit: tests=233 failures=0 errors=0 skipped=0
# lint: 0 errors, 30 warnings
```

```bash
cd android
./gradlew assembleDebug assembleDebugAndroidTest \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop --no-daemon --stacktrace
# BUILD SUCCESSFUL in 1m 3s
# applicationId      com.dailybeat.app.qa.e2eloop
# test applicationId com.dailybeat.app.qa.e2eloop.test
```

### Tests added (28 JVM + 5 policy)

| File | Covers |
|---|---|
| `capture/CapturePausedStatusTest.kt` | G2. A pause is never `OFF`; an expired deadline falls through to the real state; GPS-off wins over a stale deadline; an expired deadline over a stopped service stays `OFF` rather than being softened into "resuming shortly". |
| `util/FixAgeTest.kt` | G4. Sub-minute is "just now", minutes always round **down** so a fix is never claimed fresher than it is, negative ages from a clock change are clamped. |
| `ui/components/CaptureCoverageTest.kt` | G3. The third branch is the point: a day with nothing captured claims neither "complete" nor "has gaps", because before the first fix "No capture gaps" would be a lie of omission. |
| `ui/today/TodayMomentsTest.kt` | G1. Newest-first, independent of the DAO's `ORDER BY`, stable for same-millisecond ties, nothing dropped. |
| `ui/components/ReadableContentWidthTest.kt` | G5. No-op below 840dp, centred above it, unbounded width passed through rather than clamped. |
| `scripts/tests/test_settings_order.py` | G6. Group order, plus the product constraint asserted **separately** from the exact sequence — the sequence may legitimately change when a group is added; the constraint may not. |

Layout order and Compose layout arithmetic cannot be seen by JVM tests and need a device for
Compose assertions. Rather than leave those two gaps untested, the decision logic was split into
pure functions (`readableChildMaxWidth`, `captureCoverage`, `todayMomentsOrder`, `Formatters.fixAge`)
and the Settings order is guarded by a source-level policy test in the existing
`scripts/tests/` suite. This follows the house style already set by `Formatters.RelativeDay`:
classify in testable code, word it in string resources.

---

## QA artifact

| Field | Value |
|---|---|
| Path | `outputs/dailybeat-claude-qa-242279e/DailyBeat-claude-end-to-end-ui-242279e-QA-debug.apk` |
| Package | `com.dailybeat.app.qa.e2eloop` |
| Version | `versionCode 20`, `versionName 3.9.0` |
| Size | 63,287,127 bytes (60.4 MB) |
| SHA-256 | `9999eba436066c03c3992aed89ba106eb3c19a388aecb8252a6f9588bab0e013` |
| Signing cert | `C=US, O=Android, CN=Android Debug` — SHA-256 `d04106026b7d2ef98f552433b09252179d1930e5e9dd890ac824efcfd01922bc` |

Companion instrumentation APK: `...-QA-androidTest.apk`, package
`com.dailybeat.app.qa.e2eloop.test`, SHA-256
`18af2e428e3b50027b1b8cb3a9cd57985d6a8bd839e42be6e3013f3bfef8cc24`.

Hashes are in `outputs/dailybeat-claude-qa-242279e/SHA256SUMS.txt`.

### Isolation and signing checks

- The APK installs as **`com.dailybeat.app.qa.e2eloop`**, never `com.dailybeat.app`. Verified with
  `aapt2 dump badging`.
- It is signed with the **Android debug key**, not the release identity. The production certificate
  fingerprint `44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f` appears nowhere in
  this build and no keystore was created, moved, or read.
- The build script enforces this independently: `build.gradle.kts` requires the debug suffix to
  match `\.qa(\.[a-zA-Z][a-zA-Z0-9_]*)*`, and `verifyDisposableTestTarget` makes
  `connectedDebugAndroidTest` and `installDebugAndroidTest` fail unless the suffix is exactly
  `.qa.e2eloop`. Production package data cannot be reached by these tasks even by mistake.
- The stable `outputs/DailyBeat-v3.9.0.apk` was not overwritten; it still hashes to
  `30ce22bf…b5b83e4`. QA artifacts went to a new, separately named directory.

---

## Device evidence

This is the one area where the evidence is **incomplete, and I am not going to present it as
complete.**

### Run 1 — API 34 emulator (`kaavalan-test`), full suite

**31 tests: 29 passed, 1 failed, 1 skipped.**

- Skipped: `CloudBackupLiveTest.phoneBackupAndRestoreRoundTrip`. Correct and expected — it
  `assumeTrue`s on live Supabase credentials, which are not present on this machine. This is a
  missing secret, not a weakened gate.
- Failed: `OnboardingFlowTest.onboardingThreeStepsReachTodayScreen` — a
  `ComposeTimeoutException` after 10 s waiting for `today_list` following "Get started".

Everything that touches the six changes **passed**, including
`CaptureLifecycleTest.captureStaysOffDuringAPrivacyPause`,
`MainNavigationTest.appearanceSelectorChangesThemeAndSurvivesActivityRecreation`,
`settingsAddPlaceNeedsANameAndACapturedLocation` and `deletingNamedPlaceRequiresConfirmation` (both
scroll through the reordered Settings), `todayShowsBothMetricsWithoutHorizontalClipping`,
`todayOptionalNoteExpandSaveCollapse`, and all of `FeedScreenTest` and `WholeDayBeatTest`.

### Attribution of that failure — unresolved

I could not complete a clean same-build/same-device comparison:

- The **baseline** source (`2cc9784`, built in a throwaway worktree) **passed** that test — but on
  a *different* emulator instance, because the original one had already been destroyed.
- Re-running my branch against that second emulator failed to install
  (`cmd: Can't find service: package`) while host load average was 19→51 from other work on this
  machine. That is an environment failure, not a test result.
- The machine has since restarted and both of those emulator instances are gone, so that exact
  comparison can no longer be reproduced.

**What I can say:** I did not modify `OnboardingScreen.kt` or `MainActivity.kt` — confirmed by
`git diff 2cc9784..HEAD --name-only`. The onboarding→Today transition is owned entirely by those
two files. `today_list` itself is asserted displayed by more than twenty other instrumentation
tests that all passed on the same run. The test also carries a pre-existing in-code comment —
*"Offline emulator runners can need an extra frame to replace the onboarding surface"* — and an
explicit 10 s `waitUntil`, which is documentary evidence that this exact race was hit before my
work and was already being worked around.

**What I cannot say:** that it is definitely not mine. It remains unattributed and is the first
thing a reviewer should re-run.

### Run 2 — API 37 emulator (`Medium_Phone`), full suite: not usable

**31 tests: 27 failed**, all with the identical root cause:

```
java.lang.NoSuchMethodException: android.hardware.input.InputManager…
```

This is the known Espresso/Compose-test break on new API levels. The AVD reports
`ro.build.version.sdk=37` (Android 17); the project pins `androidx.test:runner:1.6.1` /
`androidx.test.ext:junit:1.2.1` against `compileSdk 35`. Those versions predate the `InputManager`
change, so every UI-driving test dies in the harness before touching app code.

This is a toolchain/environment mismatch, **not** a regression. Fixing it would mean upgrading
`androidx.test`, which `CLAUDE.md` constraint 10 explicitly forbids in this build and which belongs
in a separate, focused compatibility task. The gate was not weakened, skipped, or deleted — it was
run, and it is being reported as unusable on that API level with the reason stated.

### Not run at all

| Gate | Why |
|---|---|
| Live Supabase backup round trip | No `backupEmail` / `backupPassword` / Supabase config on this machine. The test skips itself cleanly rather than passing vacuously. |
| Physical-device run via `scripts/mac_phone_e2e.sh` | No phone attached. Not attempted — it would target a real device, and no disposable handset is present. |
| Visual/device matrix: 200% font scale, landscape, expanded-width rail, TalkBack traversal and announcements, 48dp target sweep, system Back from sheets/Review/full map | These need a stable emulator and, for TalkBack, manual operation. The API-34 emulator was available only long enough for the automated suite. **G5 (centred content) and G6 (Settings order) in particular have never been seen rendered on a tablet-width screen** — they are covered by unit and policy tests, not by eyes. |

I did not update any golden screenshot, relax any assertion, or remove any test to make this report
look cleaner.

---

## Known deviations recorded rather than hidden

- **Insights chart touch targets.** Each of the 7 bars has a full 132dp-tall touch target but only
  ~40dp of width on a 360dp phone, below the 48dp minimum. Widening would mean dropping to 6 bars
  or removing surface padding, both of which cost more than they return; the chart is fully
  operable through its accessibility node and the text readout above it. Recorded in the matrix as
  an accepted deviation — pre-existing, not introduced here.
- **30 lint warnings.** Unchanged in count from the baseline. Includes a deprecated
  `Icons.Outlined.Assignment` and some unused-parameter warnings that predate this work.

---

## Risks

- The unattributed `OnboardingFlowTest` failure above. Highest-priority item.
- G5 and G6 are verified by test but not by sight at tablet width.
- `readableContentWidth()` is a custom layout modifier. Its arithmetic is unit-tested and it is a
  no-op below 840dp (so no phone layout can change), but its behaviour inside the real
  `NavigationRail` row has not been observed on a device.
- The new `CaptureHealthLevel.PAUSED` enum constant is additive, and `status()` gained a defaulted
  parameter, so every existing caller and test compiled unchanged. Nothing persists the enum, so
  there is no migration surface.

---

## Recommended next action

1. On a clean **API 34** emulator, re-run
   `./gradlew connectedDebugAndroidTest -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop` and settle
   `OnboardingFlowTest.onboardingThreeStepsReachTodayScreen` one way or the other. If it fails
   again on this branch but not on `2cc9784` from the same emulator, treat it as mine and fix it
   before anything else.
2. Install the QA APK and walk the six changes by hand, especially at **tablet width and 200% font
   scale**, which nothing here has verified visually.
3. Only after that, decide about a release. This branch is deliberately left unmerged, unpushed and
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
