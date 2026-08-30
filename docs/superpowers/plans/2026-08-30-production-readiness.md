# DailyBeat Production-Readiness Plan

**Goal:** Produce a reliable DailyBeat stable release by testing every user-facing screen and feature with synthetic and adversarial data, fixing confirmed defects test-first, and verifying the signed APK on Android hardware and an emulator.

**Principles:** Prefer the smallest reliable fix; do not expose QA controls in production; do not modify or reveal the user's stored cloud API key; do not claim a bug-free release; record residual risks honestly.

## 1. Establish the baseline

- Build the current branch and run all existing Python and Android unit tests.
- Capture device screenshots and UI hierarchy for Today, Diary, History, and Settings.
- Record current package version, signing certificate, install source, and repository state.
- Preserve screenshots outside the repository under the gstack design-audit directory.

**Verify:** clean baseline build and tests; reproducible evidence for every initial finding.

## 2. Static and wiring audit

- Run Android lint and inspect all generated findings.
- Trace every visible button, toggle, field, navigation item, worker, and share/export action to its handler.
- Search for unused production code, debug-only behavior exposed in release, hard-coded secrets, unsafe logging, and missing accessibility semantics.
- Check release configuration, permissions, backup policy, ProGuard/R8 rules, ABI packaging, and cloud error handling.

**Verify:** lint report reviewed; dead or unsafe wiring recorded with file-level evidence.

## 3. Screen-wise functional matrix

- **Onboarding:** first-run navigation, required/optional permissions, denial, retry, and completion.
- **Today:** metrics, journey map, visit/event list, significant moment, voice note, optional note, report generation, and Diary navigation.
- **Diary:** empty/singular/plural event states, normal and adversarial pasted text, generation success/failure, and scrolling.
- **History:** empty state, weekly rollup, generated diary entries, export/share ZIP, and historical navigation.
- **Settings:** identity persistence, cloud provider/model/base URL, masked key handling, connection test, schedules, capture toggles, places validation/deletion, and QA-only tools.
- Exercise empty, minimal, large, duplicate, Unicode, multiline, malformed, boundary, and rapidly repeated inputs.

**Verify:** each feature has an observed result, not merely a code-path assumption.

## 4. Test-driven fixes

- For every reproducible functional bug, add a failing unit or instrumentation test first.
- Make the minimum implementation change needed to pass it.
- Re-run the focused test, then the relevant module suite.
- Keep production synthetic controls hidden while retaining deterministic debug/test fixtures.

**Verify:** each fixed defect has a regression test and focused green run.

## 5. UI/UX repair

- Correct overlaps, clipped controls, awkward wrapping, excessive dead space, inconsistent sizing, grammar, and touch/accessibility issues.
- Respect the existing DailyBeat palette, typography, and component system in `docs/DESIGN.md`.
- Capture before and after screenshots for every material visual fix.
- Commit each independent design fix atomically.

**Verify:** screenshots on representative phone and emulator sizes; no content obscured by FAB, navigation, keyboard, or system insets.

## 6. Adversarial and lifecycle testing

- Run repeated taps, repeated synthetic loads, long scrolling, rotation, background/foreground, process recreation, permission denial, empty state, large datasets, malformed place coordinates, and cloud/network failure paths.
- Use emulator location injection for journey behavior; avoid altering real phone credentials.
- Do not use Locust against a third-party billable LLM endpoint. Use local deterministic mocks for concurrency/error behavior instead.
- Repeat each critical happy path three times after fixes.

**Verify:** no fatal crash/ANR in logcat, no record multiplication from idempotent fixtures, state remains coherent, and errors are actionable.

## 7. Release gate and delivery

- Run Python tests, Android unit tests, instrumentation tests, lint, release assembly, and APK signature/ABI inspection.
- Install the signed release candidate and validate upgrade compatibility with the permanent certificate.
- Review the complete diff and scan release artifacts for secrets.
- Bump version, update release notes, commit, push, run GitHub Actions, merge, tag, and publish exactly one signed stable APK plus checksum.
- Confirm the GitHub release and Obtainium update path.

**Verify:** fresh CI evidence, signed APK metadata, matching checksum, successful install/update, and documented residual limitations.
