# Public beta validation — 2026-09-16

Candidate: **4.1.0-beta.1 / code 28**, branch `feature/public-foss-readiness`.
This is a local QA handoff, not production or store approval. Stable release marker: **4.0.6**.

## Completed checks

| Check | Observed result |
| --- | --- |
| Repository policy tests | 46 passed |
| Standard configuration JVM tests | 272 passed; 0 failures, errors or skips |
| Platform-only configuration JVM tests | 272 passed; 0 failures, errors or skips |
| Android instrumentation | 35 passed on a dedicated Android 14 / API 34 AOSP arm64 phone emulator |
| Large-text onboarding | The three-step first-run flow passed again with Android font scale 2.0 |
| Standard and platform-only APK build/lint | Passed; lint warnings remain (29 in the final standard report, no errors) |
| Google-free dependency gate | Passed: 91 resolved release artifacts, no Play Services/Firebase groups |
| Merged APK identity/permissions | Isolated QA IDs, code 28, QA launcher label; platform-only APK has no activity-recognition permission |
| Secret scan | Gitleaks 8.30.1 found no leaks in the staged patch (~248 KB); not a full-history or dependency audit |
| Whitespace validation | `git diff --cached --check` passed |

The Android suite covered offline draft persistence, local note search, template onboarding,
sharing review/cancel, actual PDF/ZIP rendering, Android JCA encryption and wrong-passphrase
rejection, capture pause/off/re-arm behavior, diary persistence, all four tabs, visit repair,
and theme persistence. It used `com.dailybeat.app.qa.e2eloop` only. The live cloud test was
explicitly excluded. These checks do not measure physical GPS recall or day-long battery use.

The first full emulator run exposed one obsolete test expecting cloud access to be mandatory;
the replacement asserts the offline draft and saved text, and the full suite then passed.
The emulator initially needed restarting after a host graphics crash; the successful runs used
the dedicated headless AVD with host graphics and Vulkan disabled.

Phone screenshots were visually inspected for the new onboarding/template controls and Carbon
surfaces. Impeccable's native/product guidance informed scrolling, minimum touch targets,
wrapping, and explicit sharing confirmation; the established Soft Sun/Carbon palette was retained.
The large-font visual check exposed a navigation-bar overlap missed by a simple click assertion.
Onboarding now applies safe-drawing insets, and the test checks each action's bounds against the
real navigation inset. This is a focused smoke test, not full TalkBack/accessibility certification.

## QA artifacts

The handoff folder contains:

- `dailybeat-4.1.0-beta.1-standard-qa.apk` — `com.dailybeat.app.qa`, Google location backend.
- `dailybeat-4.1.0-beta.1-foss-qa.apk` — `com.dailybeat.app.qa.foss`, platform location backend.
- `SHA256SUMS`, unit-test HTML reports, lint reports, phone screenshots, and `HANDOFF.md`.

Both APKs are debug/QA builds and install separately from production. They are not a signed
production update. No user's phone, production data, permanent signing identity, remote branch,
GitHub release or store listing was changed. The local debug certificate is not a distribution
signing strategy. Do not uninstall production to test either APK.

## Still required before release

1. Owner-approved open-source licence, dependency/asset provenance audit and signing strategy.
2. Approved deployment of the additive encrypted-backup table; live RLS/owner-isolation tests,
   real encrypted/legacy Android recovery drills, and retention/account-deletion implementation.
   Docker was unavailable locally: the new SQL/pgTAP tests were written, not executed.
3. Independent security review and full dependency/history scans. Local test success is not a
   security certification. Legacy plaintext backups are not retroactively removed/encrypted.
4. Physical-device field trials against a capture-off baseline, separately for Google and
   platform-only capture. The 14-day battery/recall trial has not happened; no drain guarantee.
5. Remaining roadmap engineering: immutable correction/revision history, statement-level
   source provenance, route repairs/coverage intervals, confirmed follow-ups and shortcuts.

Use [the release plan](PUBLIC_RELEASE_PLAN.md) and [full roadmap](PRODUCT_IMPROVEMENT_PLAN.md)
for acceptance criteria. Do not mark the larger T01–T22 roadmap complete based on this beta.
