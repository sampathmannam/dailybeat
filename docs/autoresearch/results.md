# DailyBeat experiment log

Starting release: v4.3.7 (`7b5a5d1723840c9aee5ff0ee2c5a61c4dffda241`).
Reference: Karpathy autoresearch `228791fb499afffb54b46200aca536f79142f117`.

## 2026-09-26 — evaluation setup

Ten synthetic map regression tests were added before implementation changes. The fixed target is route-geometry correctness, not the ML repository's validation loss and not a claimed battery score. Existing map and replay tests remain guardrails.

Frozen fixture SHA-256: `92fcede691c52c4604d610d4f002504d1483873ceb416f765741e7e2808e5354`.
Frozen evaluator SHA-256: `be80c5faaa348e7ee13e1d4e0fda7ca78ff1cabbc404462586626dc10bf3f9f3`.
Evaluation setup commit: `392451f17217187d3db7731ebfb90c949bc04c76`.

### Baseline: `baseline-01`

`python3 scripts/autoresearch_eval.py baseline-01`: 37 tests, 8 failures, 0 errors/skips. All 27 pre-existing map/replay guardrails passed; 8 of 10 new regression tests failed. Result and raw XML are in `.autoresearch/baseline-01/`.

### Experiment 1: rejected route fixes

Hypothesis: filtering an invalid breadcrumb before segmentation loses the gap and causes the renderer/replay to imply a continuous recorded route. Carry a pending gap forward to the next valid route point, across display-only stop markers, after chronological ordering.
Predeclared success: eliminate the five invalid-route/gap/replay failures while preserving all existing guardrails and the two new marker/leading-point controls. Three date-line failures are expected to remain for experiment 2. No capture frequency, stored history, provider, UI layout or dependency changes.

Candidate `e5070958e852c9008551dd7d3e804cc7921d3776`, run `candidate-01-gap`: 37 tests, 3 failures, 0 errors/skips. The five targeted failures are fixed; only the predeclared date-line cases remain. Evaluator/fixture hashes match baseline. Provisionally successful, pending the combined full-suite guardrails.

### Experiment 2: equivalent date-line coordinates

Hypothesis: a consecutive +180/-180 longitude pair represents the same meridian, but the seam interpolation divides zero by zero and produces NaN latitude. Handle this exact alias as a local north/south segment, then resume on the raw next endpoint's side. Do not modify stored points or lose latitude movement.
Predeclared success: eliminate all three remaining date-line failures, including the 16-pair/five-frame boundary grid, while keeping all previous tests passing. Keep the same fixture and evaluator as baseline.

Candidate `f964921c61d25aaf85fbc1e6f82bf4bf4567c845`, run `candidate-02-dateline`: 37 tests, 0 failures/errors/skips. All ten research regressions and all 27 existing map/replay guardrails pass. Evaluator and fixture hashes are unchanged from baseline.

### Combined decision: retain locally

Both experiments passed their predeclared correctness criteria and the full host guardrails. Retained on `research/autoresearch-20260926`, not merged or released.

| Evaluation | Tests | Failures | Errors / skips |
| --- | ---: | ---: | --- |
| Frozen baseline | 37 | 8 | 0 / 0 |
| Gap candidate | 37 | 3 | 0 / 0 |
| Gap + date-line candidate | 37 | 0 | 0 / 0 |
| Full FOSS/store JVM suite | 644 | 0 | 0 / 0 |

Additional verification:

- `python3 scripts/autoresearch_eval.py combined-full-01 --full`: passed; full XML/JSON/Gradle evidence in `.autoresearch/combined-full-01/`.
- `python3 -m pytest scripts/tests/ -q`: 119 passed, including three evaluator tests.
- From `android`: `./gradlew :app:lintDebug :app:assembleDebug :app:verifyGoogleFreeDependencies -PdailybeatFoss=true -PdailybeatStore=true -PdailybeatUnsigned=true --console=plain`: passed. Raw log: `.autoresearch/combined-full-01/build-lint.log`.
- Lint: 0 errors, 91 warnings, 1 hint. This is not a zero-warning claim; the previous release's recorded count was also 91 warnings / 1 hint.
- Google-free dependency gate: passed, 91 resolved artifacts.
- `gitleaks git --log-opts='7b5a5d1..HEAD' --redact --no-banner .`: no findings in the three setup/implementation commits.
- `git diff --check`: passed.

Limitations: no phone was attached; the other task's emulator was not used. Native rendering, live GPS/venue accuracy, battery drain, standard-build validation and final release gates still require separate checks. Desktop build/test elapsed times are diagnostic only, not a performance or battery result. No production package, backend, personal data, main branch or published release was changed.

### Continuation

The existing paused DailyBeat heartbeat was updated, not duplicated: **DailyBeat autoresearch**, active every four hours in this task. Each pass is limited to two experiments and 45 minutes, with no automatic publishing. The computer must remain on with Codex running for local scheduled work.

Next: inspect offline/capture persistence for a concrete reproducible failure; establish a new frozen synthetic regression before modifying implementation. Reuse these ten map regressions as guardrails. Do not repeat completed experiments or claim this host-only pass is a release candidate.

## 2026-09-26 05:40 UTC — retention and capture persistence pass

Starting commit: `09609e0`. The checkout was clean, with no active research build. No other checkout, device, backend or release was touched.

Investigated but not changed: the offline-download network-choice hypothesis. `KEEP` retains an existing request, but the actual UI requires pause/cancellation before changing its network choice. The normal user flow did not justify a fix.

Ten new synthetic `RetentionResearchTest` fixtures are frozen before implementation changes. This pass uses the **full** existing evaluator for baseline and candidates, so all previous map/capture/backup tests remain guardrails. The evaluator and original map fixtures are unchanged; the new retention fixture hash is recorded with the baseline.

### Experiment 3 — retention deletion must invalidate stale writers

Hypothesis: retention commits deletions without advancing the existing personal-data generation gate. A delayed editor/report save using its pre-deletion generation can therefore reinsert deleted diary text, and open readers do not receive the data-change signal.
Acceptance: stale writes are rejected after a successful non-empty prune; publish exactly one data-change notification, without altering the GPS callback generation. No-op and failed prunes must not invalidate current editors. Nested pruning during restore must not publish before the outer transaction commits (restore already owns its final notification).

### Experiment 4 — sanitize unreadable/expired GPS checkpoints

Hypothesis: retention only removes successfully decoded old checkpoints, leaving location bytes in unusable checkpoints. Removing an expired checkpoint row entirely can also reopen the legacy preference-import path at service startup.
Acceptance: scrub unusable or expired checkpoint payloads to the existing empty `{}` marker, using the same clock as retention; keep usable recent checkpoints and queued observations unchanged; roll back checkpoint cleanup if the pruning transaction fails. No changes to finalized visits, collection frequency, settings, schema or network behavior.

Initial discovery baseline `retention-baseline-01` at `b8321fc`: 654 tests, five failures, no errors/skips. Only the new retention cases failed (two stale-writer/notification cases and three checkpoint cases). Retention fixture SHA-256: `37998a86cb8de97cae4d1526a32886d15f6736edda9bb36445ae256b3fc8ed77`.

Before any implementation changes, four additional real diary-ViewModel guardrails were added: keep a retained-day draft, keep its delayed autosave, reject an expired-day draft, and never bypass a subsequent full erase. A blanket generation invalidation without retained-date handling would discard an unrelated current draft, so that is explicitly unacceptable. `retention-baseline-02` establishes the final frozen comparison including these stronger safeguards. No prior test was removed or weakened; the evaluator remains unchanged.

Final baseline `retention-baseline-02`, commit `2acad0370f7be3ac3a9bd546938fad09b1e17dcd`: **658 tests, six failures, zero errors/skips**. The additional failure is the real expired-draft flush; the retained-draft, delayed-autosave and full-erase guardrails all pass before changes. Diary fixture SHA-256: `816b9226e110d0601ba05e8c6d368cc8d1a2333916ae18585865802525dfe60d`. Both new fixtures and the existing evaluator are frozen for both candidates.

Experiment 3 candidate: publish a retention-specific generation boundary after a successful non-empty outer prune; permit only manual, date-scoped diary drafts within the retained range to survive that boundary. Ordinary stale writers remain rejected. Full erase/restore clears the exception. Expected result: three stale-writer/reader failures fixed, three checkpoint failures left for experiment 4, with all retained-draft and prior erase/restore guardrails still passing.

Experiment 3 result, candidate `c6c9b194e69a6d33c0a0fc6ad88e44c8567603a7`, `retention-candidate-03`: **658 tests, three failures, zero errors/skips**. All three stale-writer/reader failures are fixed. Current-day manual drafts, their delayed autosave, full erase, failed-prune rollback and nested-restore behavior pass. Only the predeclared checkpoint cases remain; provisionally successful pending combined gates.

Experiment 4 candidate: decode checkpoint validity using the captured retention clock; replace any unusable or expired non-empty payload with `{}` inside the existing pruning transaction. Do not remove the row and reopen legacy migration. Expected result: the three checkpoint failures become passes, with all 658 tests passing and fixture/evaluator hashes unchanged.

### Combined decision: retain locally (06:23 UTC)

Experiment 4 candidate `fc683d3135a6403da43ff1487823de7c0e906dcb`, `retention-candidate-04`: **658 tests, zero failures/errors/skips**. Both experiments meet their predeclared criteria, including preservation of retained-day drafts/autosave and rollback/erase/restore safeguards.

| Frozen full-suite evaluation | Tests | Failures | Errors / skips |
| --- | ---: | ---: | --- |
| Final baseline (`retention-baseline-02`) | 658 | 6 | 0 / 0 |
| Retention writer boundary (`retention-candidate-03`) | 658 | 3 | 0 / 0 |
| Writer boundary + checkpoint cleanup (`retention-candidate-04`) | 658 | 0 | 0 / 0 |

Verification and evidence:

- Each row used `python3 scripts/autoresearch_eval.py <label> --full` with the same JDK, SDK, unchanged evaluator and frozen test fixtures. Commands, source commit, diff hash, raw XML and results are in `.autoresearch/<label>/`.
- Retention fixture: `37998a86cb8de97cae4d1526a32886d15f6736edda9bb36445ae256b3fc8ed77`; diary fixture: `816b9226e110d0601ba05e8c6d368cc8d1a2333916ae18585865802525dfe60d`. Rechecked after both candidates; unchanged.
- Evaluator: `be80c5faaa348e7ee13e1d4e0fda7ca78ff1cabbc404462586626dc10bf3f9f3`; original map fixture: `92fcede691c52c4604d610d4f002504d1483873ceb416f765741e7e2808e5354`. Unchanged.
- `python3 -m pytest scripts/tests/ -q`: **119 passed**; log `.autoresearch/retention-candidate-04/repository-tests.log`.
- `./gradlew :app:lintDebug :app:assembleDebug :app:verifyGoogleFreeDependencies -PdailybeatFoss=true -PdailybeatStore=true -PdailybeatUnsigned=true --console=plain` from `android`: passed; log `.autoresearch/retention-candidate-04/build-lint.log`.
- Lint: **0 errors, 91 warnings, 1 hint**. Analysis tasks ran; the report output was unchanged. Google-free dependency gate passed (91 artifacts).
- `gitleaks git --log-opts='09609e0..HEAD' --redact --no-banner .`: no findings in the four setup/implementation commits; log `.autoresearch/retention-candidate-04/secrets.log`.
- `git diff --check`: passed. The research checkout was clean after implementation commits; no unfamiliar edits appeared.

Scope/limits: changes are local on `research/autoresearch-20260926`. No releases, pushes, PRs, production data, settings or backend changes. All deletion/rollback fixtures use synthetic Robolectric databases. Checkpoint cleanup is logical database cleanup, not a claim of forensic flash erasure. No new dependencies or background polling. The approved phone was absent when checked; the unrelated emulator was not used. No device/battery measurements or standard-build release qualification were performed.

The pass stops after its two planned candidate experiments, within the 45-minute limit. Next pass: investigate whether saved diary drafts remain safely invalidated across process recreation, and test repeated retention boundaries against erase/restore before proposing any further changes. These are follow-up hypotheses, not verified additional defects. Existing 658 app tests remain guardrails. Do not repeat the completed cases or call this a release candidate without the remaining release/device gates.

## 2026-09-26 — explicit user follow-up: map startup, suggestions, named areas

The user interrupted the automatic queue with a concrete map-loading/label/suggestions request,
then connected the physical phone and forbade emulator use. This was an interactive task, not
another bounded automatic experiment pass. Full evidence and the evaluator correction are in
[the map/pattern hardening report](../hardening/2026-09-26-map-patterns.md).

Local changes: first-frame native readiness that survives live GPS updates, bounded loading and
street-detail status, grounded on-device suggestions, and reactive invalidation of suggestions
when older history is hidden/changed. Full host suite: **664 passed**; repository suite:
**119 passed**; lint/build/Google-free gates passed; final targeted phone suite: **9 passed** on
`ZD2232FCR5`, disposable `.qa.e2eloop` only. No emulator, production app mutation, push or release.

At that handoff, approval of the offline town-name list was outstanding. The user's subsequent
“go” approved it; the following entry records its implementation. This did not grant permission
for coordinate-uploading lookup or automatic publication.

## 2026-09-26 — approved offline named-area follow-up

Starting at `d25f292`, the explicit user task continued in the isolated research checkout.
Frozen acceptance fixture committed first as `297d898`, SHA-256
`69b01da80c70888e819078313c627f5b169c62116cd09887e0aa7be658936bba`.
Same five-case evaluation: **baseline 3 failures → candidate 0**, with no fixture changes.
All original name/invalid-coordinate guards remained. Additional tests cover exact data identity,
206 worldwide queries against exhaustive distance calculation, legacy coordinate names, map labels
and pattern-analysis exclusion. Existing format assertions intentionally changed from coordinates
to named areas to match the user's request; no tests were weakened or removed.

Retained implementation: compact bundled GeoNames town references with CC BY 4.0 notices,
offline indexed lookup warmed on IO, nearby/far-away qualifiers, legacy fallback recognition,
and updated explanation text. No added network service, SDK, personal-coordinate cache or UI redesign.

Final host gates: **675 app tests**, **122 repository tests**, lint **0 errors / 91 warnings /
1 hint**, app/test builds and Google-free gate passed. Physical `ZD2232FCR5`, disposable QA only:
**13 passed** (Today/Diary/Days, offline index, large-text light/dark labels, native map/retry,
replay and suggestions). Synthetic native first-interactive frame **216 ms**; index decodes
**96.6–102.8 ms**, 1,000 lookups **5.8–23.6 ms**, five batches. These are bounded responsiveness
checks, not a paired speedup or battery-life claim.

Unsigned minified FOSS/store assembly and store-APK checks also passed; the optimized APK retains
the exact town index and GeoNames notice. This artifact was not installed, signed or published.

Source/evaluator/data/APK hashes, exact commands, raw evidence paths, samples, unchanged-history
assertions and limitations: [offline-area verification report](../hardening/2026-09-26-offline-areas.md).
Keep these changes local; production remains v4.3.7/code 41. No release, push, backend change,
production instrumentation, emulator or personal-history upload. Subsequent automatic work must
preserve this verified user-requested implementation and first check for overlapping work.
