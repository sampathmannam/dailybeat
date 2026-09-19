# OpenCodeReview hardening — 2026-09-19

## Outcome

DailyBeat was reviewed with Alibaba OpenCodeReview 1.12.6 and a new project-specific policy. The
review found no critical defect. Three actionable issues were fixed: release assets are now
immutable, adaptive battery standby is no longer misreported as capture being off, and the Android
instrumentation wrapper now fails closed when its working directory is unavailable.

No production app data, cloud backup, signing key, tag or release was touched.

## Review method and coverage

OpenCodeReview is used in its official delegation mode: OCR performs deterministic file selection,
exclusion and rule resolution, while the current coding agent performs the review. This avoids a
second LLM API key and keeps review credentials out of the repository.

- Tool: `@alibaba-group/open-code-review` 1.12.6 (Apache-2.0)
- Full-file preview: 346 repository/workspace entries
- Reviewable source/configuration files: 280, about 30,000 lines
- Excluded: 66 binary, generated, explicitly excluded or unsupported documentation files
- Rule groups: Android capture, GMS/FOSS parity, encrypted backup, Room/data integrity,
  cloud/geocoder/export privacy, Compose/accessibility, manifest, Supabase RLS, GitHub release
  workflows, CI/device scripts, and the built-in Kotlin/Python/configuration rules
- Change-set follow-up: every modified or added source/configuration file was reselected with
  `ocr delegate preview` and resolved against its matching rule before final validation

The committed policy is `.opencodereview/rule.json`. It deliberately includes JVM,
instrumentation and Python tests that OCR normally excludes, while excluding build output,
generated dependency inventory, APKs and keystores.

## Findings and fixes

### High — released APK assets were replaceable

The release workflow used `gh release upload --clobber` when a release already existed. A rerun
could therefore replace an APK or checksum that an Obtainium client had already downloaded under
the same semantic version.

The workflow now refuses an existing release and creates assets only once. The existing tag-to-SHA,
protected-check, checksum and permanent-signing-certificate checks remain intact. A policy test
prevents `gh release upload` or `--clobber` from returning.

### Medium — intentional low-power standby looked like capture failure

After confirmed stillness, the active location service stops and the successful motion-transition
registration becomes the low-power watcher. Today previously saw only the stopped service and told
the user that capture was off.

Capture health now has a distinct `WATCHING` state. It is intentionally not reported as healthy and
makes no fresh-point claim; it explains that Android is watching for movement and route capture
will resume on a movement transition. Capture disabled and privacy pause still take precedence,
including when a stale watcher flag exists.

### Medium — instrumentation could continue from the wrong directory

The CI wrapper used an unchecked `cd`. If the checkout path were unavailable, the script could
continue from another directory and report a confusing command failure. It now captures evidence
and exits explicitly before any Gradle/device action.

## Verification evidence

- Repository/backend/release policy: 69 tests passed.
- Android JVM: 293 tests passed, zero failures or skips.
- Android standard and Google-free variants: unit tests, lint, debug APK, AndroidTest APK and
  Google-free dependency isolation passed.
- Disposable emulator package `.qa.e2eloop`: 39 tests, zero failures, one skip. The skipped
  fused-location re-arm test requires Google Play Services, which this API 34 AVD does not provide.
- Semgrep OSS: 304 rules over 262 tracked targets, zero findings. It emitted one non-blocking
  partial-parse warning for the generated POSIX Gradle wrapper.
- Gitleaks: 280 commits scanned, no leaks.
- TruffleHog: no verified or unverified secrets.
- Trivy: zero high/critical findings in the 93-module standard and 89-module FOSS resolved runtime
  inventories; filesystem secret/misconfiguration scan also clean.
- GitHub Actions syntax and shell safety: Actionlint and warning-level ShellCheck passed.
- OpenCodeReview policy resolution was verified for capture, backup, Supabase, release workflow and
  device-script paths; the entire final change set was accounted for.

## Remaining evidence boundaries

- The independent live Supabase recovery/RLS jobs and CodeQL should run again on the pull request;
  this local review did not copy protected QA credentials to the developer machine.
- Battery benefit and route recall still require the documented matched physical-phone field trial.
- One local emulator cannot replace Google Play Services, OEM background-kill, TalkBack, large-font,
  landscape and independent security review evidence.
- OpenCodeReview delegation improves deterministic coverage but is not a formal security audit.
