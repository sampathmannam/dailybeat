# OpenStreetMap Journey Implementation Plan

**Goal:** Replace DailyBeat's decorative journey preview with a tested interactive OpenStreetMap map that displays today's route and stops.

**Architecture:** A pure Kotlin journey model validates and orders visit coordinates. A Compose wrapper owns a MapLibre `MapView`, loads OpenFreeMap's style, renders GeoJSON layers, fits the camera, and exposes a full-map action. Today remains usable if map loading fails.

**Tech stack:** Kotlin, Jetpack Compose, MapLibre Native Android 11.8.0, OpenFreeMap vector style, JUnit, Compose instrumentation.

### Task 1: Lock journey geometry behavior with tests

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/ui/components/JourneyMapModel.kt`
- Create: `android/app/src/test/java/com/dailybeat/app/ui/components/JourneyMapModelTest.kt`

1. Write failing tests for invalid-coordinate filtering, chronological ordering, bounds, single-point padding, GeoJSON, and openstreetmap.org URL generation.
2. Run the focused test and verify it fails for missing production code.
3. Implement the smallest pure model that passes.
4. Re-run the focused test.

### Task 2: Add the interactive map

**Files:**
- Modify: `android/app/build.gradle.kts`
- Replace: `android/app/src/main/java/com/dailybeat/app/ui/components/JourneyMapPreview.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

1. Add the maintained MapLibre dependency.
2. Replace the Canvas preview with a lifecycle-safe MapView hosted in Compose.
3. Load the OpenFreeMap Liberty style, add route and stop layers, and fit the camera.
4. Keep attribution visible and add the external full-map action.
5. Isolate style/render failures behind an accessible error state.
6. Compile the debug app and correct only integration errors.

### Task 3: Test screen wiring and provider identification

**Files:**
- Modify: `android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/geo/OsmGeocoder.kt`

1. Add an instrumentation assertion that synthetic journey data reveals the real map and full-map action.
2. Give Nominatim a stable DailyBeat User-Agent with the public repository URL.
3. Run focused instrumentation on `emulator-5554`.

### Task 4: Adversarial verification

1. Run all unit tests and lint.
2. Run all instrumentation tests on `emulator-5554`.
3. Visually verify map tiles, route, stops, attribution, scrolling, pan, zoom, and rotation.
4. Repeat the Today journey flow three times with synthetic data.
5. Test network-off behavior and restore network afterward.
6. Run a monkey pass and inspect logs for DailyBeat crashes or ANRs.

### Task 5: Release candidate and shipping

1. Bump to the next minor version and version code.
2. Build the signed release APK and inspect its native ABIs.
3. Upgrade the existing physical-device installation and verify user data remains present without reading secrets.
4. Run final critical-flow checks, review the diff, commit, push, merge, and publish one stable APK plus checksum through the existing release workflow.
