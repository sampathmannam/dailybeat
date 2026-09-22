# Personal-journal tracking reliability

Research and implementation review: 21 September 2026. DailyBeat helps someone remember and
correct their day. It does not certify attendance, entry to a business, or continuous presence.

## Research-informed decisions

- Android reports horizontal accuracy as an estimated radius with 68% confidence, not a boundary
  inside which the true position is guaranteed. We use it to avoid treating small GPS shifts as
  travel and distinguish approximate capture in the UI. These are conservative journal heuristics,
  not a statistical guarantee. [Android Location reference](https://developer.android.com/reference/android/location/Location#getAccuracy()).
- Reverse geocoding selects a nearby indexed map object; it may return a different street or an
  adjacent business. A classified venue within 50 metres is therefore labelled `Near <name>`.
  User-saved names remain the preferred labels. [Nominatim reverse-geocoding contract](https://nominatim.org/release-docs/latest/api/Reverse/).
- Android recommends cleaning up unused location requests and batching updates. Request intervals
  and priorities are unchanged; this pass fixes listener removal and motion-wakeup delivery rather
  than increasing polling. [Android battery guidance](https://developer.android.com/develop/sensors-and-location/location/battery/optimize).
- AndroidX wraps location listeners on some platform versions; removal must use the matching
  compatibility API. The platform source now does that.
  [AndroidX implementation](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/location/LocationManagerCompat.java).
- Motion results arrive as provider-added intent extras. The narrowly explicit, private broadcast
  token must allow that fill-in. Old tokens are cancelled; stale asynchronous registration results
  cannot re-arm capture after disarm or cancel a newer subscription.
  [Activity-recognition callback](https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient),
  [PendingIntent mutability](https://developer.android.com/reference/android/app/PendingIntent#FLAG_IMMUTABLE).

## Reproduced problems and fixes

| Problem | Change and regression coverage |
| --- | --- |
| Slow walking dragged the stop's moving anchor along the route. | Fixed 150-metre anchor; slow-walk and stationary-jitter traces. |
| A lone fix or long silent interval became a long stay. | Stop ends at its last observed in-radius fix; gaps over ten minutes split observations. |
| Moving travel could be mistaken for arrival; first stop minutes were lost. | Confirm eight minutes around a fixed arrival candidate, then use its first timestamp. |
| A loop ending at its origin lost the journey. | Persist maximum displacement during unfinished travel, including process restart. |
| Weak fixes could move a stop to another street. | Reported uncertainty above 150 metres cannot establish or terminate a stop. |
| Stationary heartbeat jitter accumulated distance. | Uncertainty-aware distance anchor; keep heartbeats for route coverage and reset across gaps. |
| A nearby business looked like a confirmed visit. | Strict coordinate/category/proximity checks; `Near` labels; expire derived cache after 30 days and retire old confident guesses. |
| Frequent-place suggestions merged a chain of nearby locations. | Bound the whole cluster and avoid conflicting names; exclude hidden, invalid, private and already-saved visits. Suggestions still require user action. |
| Failed or late provider callbacks kept work alive. | Matched listener cleanup, terminal fallback guards, coroutine cancellation and registration generations. |
| UI called a weak recent fix reliable, or missing updates “no movement”. | Separate approximate location from missing location; explain possible gaps without claiming stillness. |

Existing finalized visits, manual names, private-zone settings, network consent and database schema
are preserved. Only unfinished capture gains an optional backward-compatible checkpoint field.
Distance display is recalculated from existing route observations; raw history is not rewritten.
No new dependency, account requirement or enabled-by-default network service is introduced.

## What remains imperfect

- Eight minutes and 150 metres balance false stops against missing short stops. Very brief shop
  visits or adjacent venues can still be missed or grouped. A GPS trace cannot reliably distinguish
  two shops in one building. Saved-place naming and manual correction remain important.
- A 50-metre map centroid filter may reject a large venue whose map centre is farther away. It is
  safer to show an address/unnamed place than invent the venue. No 95% accuracy claim is made.
- Offline capture and saved names work without a lookup service. New automatic business names
  need the user's enabled/configured endpoint and map coverage; downloaded map tiles are not an
  offline searchable business directory. This pass does not enable such a service silently.
- Unknown time stays unknown. Conservative gaps can split one real visit into multiple segments.
  Distance can undercount tight turns or short motion inside reported uncertainty.
- Emulator and deterministic traces cannot establish real-device battery drain, OEM background
  survival, indoor GPS quality or delivery of Google activity events. Follow
  [the battery field trial](BATTERY_FIELD_TRIAL.md) with an independently noted route and stop times.
  Record missed/false stops, venue corrections, restart gaps and battery impact separately.

## Verification targets

Run both standard and Google-free unit/lint builds, Google-free dependency verification and
repository policy checks. Run the store release build separately. Relevant regression suites:
`VisitTracker*`, `RoutePointSamplerTest`, `CaptureProcessorTest`, `DayFeedBuilderTest`,
`OsmGeocoderTest`, `GeocodingConsentTest`, `FrequentPlaceLearnerTest`, `GeofenceMatcherTest`,
`PrivateZoneGeocoderGateTest`, `CaptureHealthTest`, `CapturePausedStatusTest`,
`PlatformLocationSourceTest`, `RecoveringLocationSourceTest`, `MotionWatcherRegistrationTest`,
and `MotionTransitionPendingIntentTest`.

UI checks on a disposable emulator: `CaptureStatusClarityTest` at 200% font size in light/dark
mode, existing large-text navigation and offline/manual-name flows. Actual run results should be
recorded in the handoff; the list above is not itself a claim that tests passed.
