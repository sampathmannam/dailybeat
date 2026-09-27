# Named offline areas — verified local follow-up

The user's subsequent “go” approved the proposed offline town-name list. This was a continuation
of the explicit map/label/suggestions task, not permission to publish or mutate the production app.
Work stayed in `dailybeat-autoresearch` on `research/autoresearch-20260926`, starting at `d25f292`.
The checkout was clean; no competing experiment or user edit was found there. Other worktrees and
their outputs were left alone. No emulator was used or stopped.

## Hypothesis and fixed acceptance test

A local, immutable settlement reference can replace unreadable coordinate fallback text without
pretending to identify a business. Actual addresses, saved places and manual corrections must win;
invalid coordinates must not invent a name; remote points must show a reference distance instead
of falsely saying a town is nearby. Both old and new approximate labels must remain unconfirmed.

`OfflineAreaLabelsTest` was committed before implementation as `297d898`. Its SHA-256 stayed
`69b01da80c70888e819078313c627f5b169c62116cd09887e0aa7be658936bba` throughout the comparison.
The same Gradle command, properties and five cases were used for baseline and candidate:

```sh
# From android; JAVA_HOME=Android Studio bundled JDK, ANDROID_HOME=installed Android SDK
./gradlew :app:testDebugUnitTest --tests com.dailybeat.app.domain.OfflineAreaLabelsTest \
  -PdailybeatFoss=true -PdailybeatStore=true -PdailybeatUnsigned=true \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop \
  --max-workers=1 --no-parallel --console=plain
```

- Baseline: **5 tests, 3 failures, 0 errors/skips**. Coordinates still displayed, remote reference
  missing, and the new approximate label not recognized. Existing name/invalid-fix guards passed.
- Candidate: **5 tests, 0 failures/errors/skips**. No evaluator changes or relaxed acceptance.
- Additional regressions: resource hash/count, 206 deterministic worldwide queries compared with
  independent exhaustive great-circle search, malformed input, legacy raw coordinate names,
  map-stop labels, and exclusion of approximate names from confirmed pattern recurrence.
- Pre-existing exact coordinate-format expectations were changed to the newly requested town-name
  format, retaining their address/manual/hidden-history/data-mutation assertions. The legacy
  coordinate-format recognition tests remain. No tests were disabled or deleted.

Implementation/data design, source hash and CC BY 4.0 attribution are in
[offline-place-names.md](../offline-place-names.md). The 31,638-reference resource is 957,727 bytes.
It is warmed off the UI thread and does not persist query coordinates. A caller racing initial
warmup waits for the same one-time load; there is no permanently cached transient placeholder.

Impeccable informed the plain-language approximation notice and readable labels. The existing
yellow/carbon palette, navigation, cards and disclosure behavior were kept unchanged.

## Host verification

The final full FOSS/store run passed **675 tests, 0 failures/errors/skips**, lint (**0 errors,
91 warnings, 1 hint**), debug app/test APK assembly and Google-free verification (**91 artifacts**).
The earlier full run before the added map-label guard and final wording also passed all 674 tests.
Repository policy tests: **122 passed**. `git diff --check` passed. The converter reproduced the
committed resource exactly with `--check`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:verifyGoogleFreeDependencies \
  -PdailybeatFoss=true -PdailybeatStore=true -PdailybeatUnsigned=true \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop \
  --max-workers=1 --no-parallel --console=plain
python3 -m pytest scripts/tests/ -q
```

All Gradle work used one worker and no parallel projects. `:app:assembleRelease` with the same
FOSS/store/unsigned flags (without a debug suffix) also passed, including R8/resource shrinking
and release vital lint. The optimized APK contains the exact reviewed town-index bytes and the
complete GeoNames notice. `scripts/check_store_apk.py` passed release identity, permissions,
licences and Google-free-code checks using SDK 35.0.0 `aapt`.
Unsigned optimized APK SHA-256:
`35181b2901d01ade9b9ba8c152be24efcb1118637257b19f21a781f746f6a3f5`.
It was not installed on the phone, signed or published; phone tests use the debug package above.

## Physical phone verification

Device: Motorola Signature `ZD2232FCR5`. Installed only disposable `com.dailybeat.app.qa.e2eloop`
and its instrumentation package. Production `com.dailybeat.app` remained v4.3.7/code 41.
No production/ordinary-QA clear, uninstall, force-stop, instrumentation or personal-history read.

**13 tests passed, 0 failed**, in 17.077 s:

- Two complete Today/Diary/Days naming flows with online maps disabled and the geocoder endpoint
  empty: fallbacks render, saved names update, manual corrections win, original visits/events stay
  identical, and the name-entry field starts empty instead of saving the approximate label.
- Fresh index decoding and 1,000-lookup timing batches using synthetic public coordinates.
- Approximate moment labels at 200% text in light and dark themes.
- Native first-frame readiness with deliberately unavailable street tiles, visible route pixels,
  live route updates and bounded loading; failed-style recovery; six replay regressions.
- Private pattern suggestions in both themes at 200% text.

Observed first interactive native map: **216 ms** for that synthetic missing-background fixture.
It does not measure live-provider street download time. The same test verifies the loading state
ends and later GPS-point updates do not bring back a blocking preparation screen.

Predeclared responsiveness guards were <1,000 ms per index decode and <150 ms per 1,000 lookups.
All five samples passed, including the first batch:

| Measurement (ms) | Samples |
| --- | --- |
| Fresh resource decode | 98.179, 101.425, 98.809, 102.833, 96.625 |
| 1,000 synthetic lookups | 23.611, 6.220, 6.100, 6.069, 5.825 |

These are fresh index objects in a running test process, with OS/runtime caches and possible JIT
effects, not cold-process measurements or a paired speedup claim. No all-day battery, thermal or
real-trip place-accuracy claim follows from these samples.

Synthetic Compose-root screenshots of Today, Diary, Days, large-text moment cards and the visible
native route were inspected. No whole-phone/notification screenshots were taken in this pass.

## Raw evidence and identities

Local ignored evidence: `.autoresearch/area-20260926/`. Includes `baseline.log/.xml`,
`candidate.log/.xml`, `host-full.log`, `host-final.log`, full `unit-test-results/`, `lint.txt`,
`repository-tests.log`, `phone-01.txt`, `map-startup-timing.txt`, synthetic screenshots and exact
samples in `phone-evidence/timings.txt`, plus `unsigned-packaging.log`.

SHA-256 identities:

- Tested app APK: `40366cd35d16e44010b64b999d6167dc927505bd9a7a39aef464fe46454ed0be`.
- Tested instrumentation APK: `cca070e27a136c0606e4c37f968c3296eba109b3fe775f469980f4e211ca462f`.
- `OfflineTownIndex.kt`: `136aa439048057da06a14f9e81db37a6a827a96a6105bc8316b254b295c90a33`.
- `VisitLabels.kt`: `feee0001890f68b5bd0ec036dac163898a8578a6689c3428f17559960dfa1f67`.
- Converter: `1936abf6155e420d94be320d3d766951cebdfd173c85b2c8efdb3af6b824b6be`.
- Exhaustive-index fixture: `4ac3ca1bd57f5a2682fbdb78a6ee3b7e492bbd36497dcd5fb6e5da9c6bb198db`.
- Phone timing fixture: `0d8e366824f816ae3486a7ac72663049932abca5a5ea9b06716f0c942ce8882e`.
- Phone large-text fixture: `1c0030314e12dec1c885194514d08c4a2891f6469b5bcce86aa62f7363e30941`.
- Phone naming-flow fixture: `2f56805c54fdfff97fd75a4e62d3c22c8a245a762e21f9b15873725c61eae548`.

Decision: retain the verified named-area improvement locally. No push, PR, tag, version bump,
signed release, store submission, production install or backend change. Standard-build release
qualification and real-world business-name/battery measurements remain separate work.
