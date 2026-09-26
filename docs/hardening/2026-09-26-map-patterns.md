# Map startup and private pattern suggestions — 26 September 2026

Explicit user request, based on research commit `8c6af83`. This is local work, not a release.
The user selected the connected physical phone and explicitly rejected emulator testing.
Only `ZD2232FCR5` and disposable `com.dailybeat.app.qa.e2eloop` were used for instrumentation.
Production remains v4.3.7 / 41; its database, permissions and capture service were not changed.
Builds used `--max-workers=1 --no-parallel` to limit laptop load. No other checkout was edited.

## Changes

- **Interactive map:** the first drawn frame of the route is usable without waiting for every
  background tile and glyph. The initial camera moves directly into position. Updating live
  geometry no longer reinstates the loading cover. The fallback does not download a second map
  while the native renderer is starting. The bounded initialization deadline now includes native
  startup, and it never cancels unrelated maps' shared HTTP requests. Separate, plain-language
  street-detail status retains a retry action without covering an already usable route.
- **Suggestions:** at most two locally derived suggestions: name unconfirmed stops, review an
  unusually stop-heavy day, add context to recurring visits, or review after a repeated travel
  period. Recurrence needs multiple dates; travel suggestions need at least three journeys on
  two dates; a busy-day comparison needs three prior active days. Hidden, future and out-of-window
  history cannot contribute. Nearby/coordinate placeholders are not promoted to confirmed venues.
  These are transparent rules, not a newly added cloud model or conclusions about activities.
- **Live privacy correction:** observe the entire 28-day window, not only changes to today's
  visits. Hiding an older visit now immediately removes its recurring-place suggestion.
- The Impeccable Android/clarity guidance informed usable loading states, readable explanations,
  and existing Material/theme tokens. No palette or navigation redesign. The requested skill
  update failed with a bundle-verification 404; nothing was installed, so the installed skill
  remained in use.

## Evidence and limitations

Local raw evidence is in `.autoresearch/ui-20260926/` (ignored, not public telemetry).

- Fixed native startup fixture: synthetic two-point route, local style, unavailable background
  raster tiles, network opt-out. With the original map implementation restored temporarily,
  the interactive-ready condition failed its **5-second** deadline (`map-baseline.txt`). The
  changed implementation met that same deadline in early runs (344 ms and 391 ms observed).
  Pixel assertions separately verify a visible native route; a readiness tag alone is not enough.
  This is a failure-mode responsiveness result, not a claim that every real map loads in 0.4 s.
- An initial fixture omitted its coverage file and was invalid; it is not evidence of an app
  defect. Later combined phone runs had rendering timeouts, including an unchanged Canvas replay
  test; isolated replay passed. Those failures remain recorded, not silently discarded.
- A new street-detail check incorrectly required an error-only Retry button even when the SDK
  had settled the missing tiles as a complete frame. Extending the observation window from two
  to fifteen seconds did not fix that assumption. The synthetic semantics tree proved the map
  was ready, replay enabled and no loading banner remained. That extra evaluator assertion was
  invalidated: the corrected check requires bounded loading, whether the SDK settles or times
  out, while the independent five-second startup and pixel assertions remain unchanged. The
  corrected fixture is rerun against the original implementation before the final comparison.
  In that corrected baseline (`map-corrected-baseline.txt`), the original implementation opened
  initially but lost `journey_map_ready` after a live route update and the deadline. Its render
  effect resets readiness for model changes, while the style startup watchdog is already spent.
  This reproduces a continuous-loading case independently of cold-start network timing.
- `PatternHistoryRefreshTest`: baseline failed after hiding an older visit without changing
  today (`pattern-refresh-baseline-02.log`); the reactive-window implementation passes unchanged.
- Full FOSS/store host suite: **664 tests, 0 failures/errors/skips**. Raw XML copied to
  `unit-test-results/`; build/lint output in `host-complete.log`.
- Repository policy suite: **119 passed**. Lint: **0 errors, 91 warnings, 1 hint**.
  Debug app/test APK builds and the Google-free dependency gate (91 artifacts) passed.
- Light/dark suggestions inspected on the phone at 200% text using synthetic history; screenshots
  remain local. No personal location history was used as a fixture or sent to a geocoding/cloud
  service. A whole-phone diagnostic capture unexpectedly included personal content after the test
  Activity closed; that temporary file was immediately deleted, not kept as test evidence.

The tested app APK SHA-256 is
`a5112586ffe4acd216d59fee8398013a3d66ab6dd563149a50606c23376a0f11`.
Final phone regression: **9 passed, 0 failed**, 12.046 seconds (`phone-verified.txt`): native
startup/pixel/live-update readiness, failed-style recovery, six replay checks, and suggestions
in both themes at large text. The final test APK SHA-256 is
`c7fbe658d551fa232394a114a22e72e6dfa6a8509602dd76d26c3e6253848890`.
Corrected startup fixture SHA-256:
`16dc52ae04c8e089067ddec4739aff453be2f750561814fdc5cb2ac5326ac2e2`.
Map implementation SHA-256 (identical to the full host-verified source):
`8ae47c095ee00368e22c0a73eba8512bc353d213ddb3bcb6d5e20e46c77d9bde`.
Privacy-refresh fixture SHA-256:
`c135df8cc2032865c3650f5ca2bf820b6bd17b5cdbffbdf7596438717751064c`.
The final rebuilt package and updated instrumentation fixture also passed lint/build again
(`final-package.log`). Initial failing/inconclusive runs remain beside the final evidence.
No battery savings, universal map availability, absence of all bugs, standard-build release
qualification, signed release, store submission or production installation is claimed.

## Still pending: named approximate areas

The coordinate fallback in `VisitLabels` has **not yet been changed**. The user has been asked
to approve bundling an offline town-name list instead of adding a new coordinate-uploading
geocoder. This decision is outstanding; do not treat the selected default in the question as
approval. The existing precise/saved/corrected names continue to take precedence.

GeoNames' [official extract documentation](https://download.geonames.org/export/dump/readme.txt)
describes the city list and CC BY 4.0 attribution requirement. A public `cities15000.zip` was
downloaded for evaluation only; it is not packaged or committed. It contains distinct records
for Rasipuram, Namakkal and Velur. Source archive SHA-256:
`9c1f26fa632212ad77e5b82b6d5df3b019a3c119e423efe59d7224a186f9c9f4`.

If approved, implement a bounded, on-device, licensed named-area lookup with honest relative
labels, warm it off the UI thread, test historical Today/Days/Diary labels and invalid coordinates,
retain manual corrections, exclude private/hidden history, and measure lookup cost on this phone.
Never infer an exact business visit from a nearby town. Do not enable automatic network lookup
or publish this branch as part of that approval.
