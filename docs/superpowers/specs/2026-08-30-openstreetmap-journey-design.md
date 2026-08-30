# DailyBeat OpenStreetMap Journey Design

## Goal

Replace the decorative journey drawing on Today with a real, interactive map that shows the user's recorded journey and remains simple enough to operate reliably.

## User experience

- When today's valid location visits exist, Today shows an interactive OpenStreetMap-based map before the visit list.
- The map draws the visits in chronological order as a route and marks recorded stops.
- The camera fits all recorded points, including the single-point case.
- Standard pan and zoom gestures work inside the map.
- Visible map attribution remains enabled.
- An **Open full map** action opens the same journey area on openstreetmap.org.
- If map tiles cannot load, DailyBeat keeps the journey list usable and shows an honest map-unavailable message instead of crashing.
- When there are no visits, the existing journey empty state remains unchanged.

## Technical design

- Use MapLibre Native Android `13.6.0`, the current maintained Android release verified from the project's official releases.
- Use OpenFreeMap's Liberty style at `https://tiles.openfreemap.org/styles/liberty`. It is based on OpenStreetMap data, requires no account or API key, and its official mobile guidance supports MapLibre Native.
- Host MapLibre's `MapView` inside Compose with `AndroidView` and forward Android lifecycle events.
- Add a GeoJSON route source with a line layer and a point layer. Keep all geometry generation in a small pure Kotlin model so coordinate filtering and ordering can be unit-tested.
- Keep attribution and logo controls visible. Do not implement tile prefetch or offline map downloads.
- Keep the style URL in one constant so a future provider change is surgical, without adding user-facing provider configuration.

## Reliability and privacy

- Only viewport tiles are requested while the user views the map.
- Journey coordinates go to the map tile provider only indirectly through requested viewport tiles; no diary text, API key, or identity is sent.
- Invalid, non-finite, or out-of-range coordinates are excluded before creating map geometry.
- Map loading failure is isolated from diary, visit cards, and cloud-report features.
- The existing Nominatim reverse geocoder must use an accurate, stable DailyBeat User-Agent and repository contact URL.

## Verification

- Unit tests cover coordinate validation, chronological route ordering, bounds, and OpenStreetMap URL generation.
- Instrumentation verifies that synthetic visits expose the interactive map and full-map action.
- Build, unit tests, lint, and Android instrumentation must pass.
- Emulator QA verifies tile rendering, attribution, route/markers, gestures, failure-safe behavior, and three repetitions of the critical journey flow.
- Release APK ABI contents must be inspected so MapLibre does not reintroduce the prior emulator ABI installation failure.

## Deliberate exclusions

- No offline tile packs or background tile prefetch.
- No navigation or turn-by-turn directions.
- No paid map API key.
- No custom map-style editor or provider settings.

