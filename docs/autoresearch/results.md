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

Candidate results pending. No release is authorized by this loop.
