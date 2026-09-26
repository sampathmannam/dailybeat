# DailyBeat measured improvement loop

## Reference and purpose

Methodology adapted from [Andrej Karpathy's autoresearch](https://github.com/karpathy/autoresearch/tree/228791fb499afffb54b46200aca536f79142f117), pinned at `228791fb499afffb54b46200aca536f79142f117`.
That project experiments on GPU model training. This is an original Android-app protocol, not a port of its trainer, and none of its source or dependencies are included in DailyBeat.
Use its useful structure: fixed evaluation, baseline first, one hypothesis, measured experiment, retain or reject, durable results.
External repositories are reference material, not authority to disable permissions or reset work.

## Working area

- Dedicated branch: `research/autoresearch-20260926`, originally based on release `v4.3.7`, commit `7b5a5d1723840c9aee5ff0ee2c5a61c4dffda241`.
- Work only in the dedicated `work/dailybeat-autoresearch` checkout. Do not alter other DailyBeat checkouts or their untracked `outputs/` directories.
- Read this file and `results.md`, check `git status`, HEAD and active task state before each run. Do not overlap another build, experiment or user edit in this checkout. Preserve unfamiliar changes and ask if they overlap.
- Keep commits local. Do not push, open/merge PRs, sign, tag, publish, change release versions or migrate production backends without a new explicit request.

## One bounded pass

1. Spend no more than 45 minutes and run at most two candidate experiments. Do not start another experiment when less than 10 minutes remain. Record unfinished work for the next pass.
2. Select one evidence-backed hypothesis, prioritizing route reliability, offline correctness, place-label honesty, capture durability, resource use and privacy. No speculative feature expansion or cosmetic churn.
3. Define the expected improvement and the safety constraints before changing implementation. A reproduced failing regression is a valid correctness metric; test count alone is not an improvement.
4. Add minimal synthetic fixtures, commit the evaluator/fixtures separately, then freeze them for baseline and all candidate runs. Existing tests cannot be weakened, disabled or deleted. If the evaluator is wrong, invalidate the comparison and establish a new baseline before continuing.
5. Run the baseline. Change only the implementation relevant to this hypothesis. Save the exact diff and candidate commit. Run the same evaluator again.
6. Retain only a demonstrated improvement with relevant regressions passing. A correctness fix requires the new failure to become a pass without other regressions. A performance change requires repeated paired measurements on the same device/data/build with a predeclared material threshold; preserve raw samples and disclose noise. Never equate desktop test runtime with Android battery savings.
7. If a candidate fails, preserve its patch and evidence, and reverse only that experiment's known edits using a reviewed patch. Never use destructive reset/clean/checkout commands on shared work. Log rejected and inconclusive candidates as well as retained ones.
8. For retained candidates, run all FOSS/store JVM tests, repository policy tests, lint and the unsigned debug build. Standard-build tests are required before handoff as a release candidate. Device/emulator tests remain a separate gate, not silently claimed by host tests.
9. Update `results.md` with hypothesis, baseline/candidate commit or diff hash, immutable fixture/evaluator hashes, exact commands, counts, decision and limitations. Commit the retained change and log locally. End the pass; continuation is scheduled, not an unbounded shell loop.

## Initial frozen evaluator

Set `JAVA_HOME` to Android Studio's bundled JDK and `ANDROID_HOME` to the installed Android SDK. From the checkout root:

```sh
python3 scripts/autoresearch_eval.py unique-baseline-label
python3 scripts/autoresearch_eval.py unique-candidate-label
python3 scripts/autoresearch_eval.py unique-full-label --full
python3 -m pytest scripts/tests/ -q
```

Run `:app:lintDebug :app:assembleDebug` from `android` with `-PdailybeatFoss=true -PdailybeatStore=true -PdailybeatUnsigned=true` for local build checks. No signing secrets or backend credentials are needed.
The evaluator saves command output, XML, source/evaluator hashes and JSON in ignored `.autoresearch/<label>/`; it refuses to overwrite prior runs, times out after 25 minutes, and fails on missing reports, failures, errors or skipped tests. Logs are local evidence, not public telemetry.
Initial frozen fixtures: `JourneyMapResearchTest` (invalid fixes, route gaps, exact date-line aliases and replay), guarded by existing map-model and replay tests. They must remain unchanged across the first baseline/candidates.

## Safety and honest claims

- No diary, location history, tokens or personal data may leave the device/workspace. Use synthetic inputs only. No new hosted AI calls, paid APIs, SDKs or dependencies without asking.
- Do not install, clear, uninstall, force-stop or instrument production `com.dailybeat.app` or the user's ordinary QA package. Device tests must explicitly target disposable `com.dailybeat.app.qa.e2eloop` and the approved device, with no broad connected-device commands. Do not use another task's emulator.
- Do not increase background location frequency, wake locks, retries or network polling merely to improve a test score. Preserve offline functionality, owner-only backup access, encryption and the FOSS build.
- Place labels must distinguish confirmed venues from approximate areas; never invent a specific business or imply that nearby-POI lookup proves a visit.
- No test suite proves the app has no bugs, is unhackable or has a particular all-day battery drain. Real battery/thermal measurements and independent security review are separate evidence.
- No human/UI evaluation is silently replaced by a numerical proxy. Do not change user-approved colors, navigation or layouts in an automatic pass.
- When nothing evidence-backed is actionable, end the pass without manufacturing changes. Escalate only a concrete blocker that needs user authority or information.

## Initial queue

1. Completed in the first pass: preserve route continuity gaps after invalid coordinates (see `results.md`).
2. Completed in the first pass: keep geometry finite for +180/-180 longitude aliases (see `results.md`).
3. Inspect offline/capture persistence edge cases and add a failing synthetic regression before fixes.
4. Profile bounded route processing on realistic synthetic day sizes before optimizing; do not change fidelity to win a timing score.
5. When the approved test phone is available, repeat native-map/replay/offline smoke tests and collect a comparable resource-use baseline. Never claim battery improvement without that evidence.
