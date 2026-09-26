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
