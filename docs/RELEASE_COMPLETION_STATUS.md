# Release completion attempt — 2026-09-16

The owner requested completion and a release. The candidate is still **4.1.0-beta.1 / code 28**;
the published stable release remains **4.0.6**. No stable release is claimed by this document.

## Work completed in this attempt

- The publisher now waits for `foss-build` and `rls-tests` in addition to its existing required checks.
- Backend isolation tests run on every main/PR commit so a documentation-only release commit
  cannot skip the database gate or wait forever for a path-filtered job.
- The Mac installer reads the release marker instead of an obsolete hard-coded tag. It verifies
  the exact asset checksum, permanent signing certificate, package and version before `adb install -r`.
  There is no checksum-less fallback, uninstall, downgrade flag or automatic permission grant.
- Synthetic installer tests cover success and rejection of corrupt, ambiguous, mis-signed,
  wrong-package and wrong-version assets. These tests use fake Android tools, not a real device.
- All **56** repository policy/installer tests pass locally; shell syntax and whitespace checks pass.
- Gitleaks scanned **260 commits / 6.32 MB** of repository history with no detected leaks.

The app code remains the tested public-beta milestone in [the validation record](PUBLIC_BETA_VALIDATION.md).
This work does not complete the still-open evidence/revision, correction, follow-up and capture
coverage tasks in [the roadmap](PRODUCT_IMPROVEMENT_PLAN.md).

## Release gates currently awaiting evidence or owner input

- **Licence:** the owner has been asked to select GPL-3.0 or Apache-2.0; no choice has been received.
- **Backend:** `supabase projects list` fails because the CLI is not signed in. No privileged
  migration/deployment was attempted. Sign in locally with `supabase login`; do not paste tokens.
- **Physical device:** `adb devices -l` currently lists no devices. No phone data or permissions
  were changed. Device capture/recovery verification and sustained battery/recall trials are unrun.
- **Remote checks:** the candidate is being submitted to GitHub CI. Local Docker is unavailable;
  the GitHub Backend RLS job is the available route to executable migration/isolation tests.
- **Public-store readiness:** dependency/asset licensing, account deletion/retention, independent
  security review and the remaining product acceptance gates are not signed off.

Do not create a production tag or bypass required checks to turn a QA artifact into a release.
Use the existing permanent signing identity and retain the installed production database.
