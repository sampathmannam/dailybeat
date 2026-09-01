# PatrolGrid

PatrolGrid is an Android patrol-coordination app for subdivision supervisors and field patrol personnel. It turns informal location updates into clear patrol missions while preserving field judgment and explicit tracking boundaries.

## Current build

The first PatrolGrid vertical slice includes:

- A shared Android app with supervisor and patrol-personnel roles.
- A mission-first **Patrol Control** view for active patrols, exceptions, upcoming duties, and contextual review.
- A supervisor assignment sheet for selecting a route plan, patrol unit, priority locations, duty window, and suggested-route versus flexible-area guidance.
- A dark **My Patrol** view for duty briefing, priority locations, route context, observations, operational deviations, and patrol completion.
- Location tracking that is off by default, starts only with an active patrol, stores AES-256-GCM-encrypted local route evidence against the mission, and stops when the patrol ends.
- Human-review outcomes instead of employee scores or automatic misconduct findings.
- Offline-ready local mission state, Room persistence for route points, Material 3 accessibility, and day/night themes.

The current role switch and sample missions are local development scaffolding. Production role access, assignment synchronization, and supervisor-to-device data exchange still require the PatrolGrid backend.

## Build

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Product principles

- Mission before movement.
- Exceptions before surveillance.
- Evidence always includes operational context.
- Routes support field judgment; they do not replace it.
- Tracking is visible, explicit, and bounded to duty.

See [PRODUCT.md](PRODUCT.md) for the product definition, [docs/DESIGN.md](docs/DESIGN.md) for the UI system, and [docs/SECURITY.md](docs/SECURITY.md) for implemented controls and pre-deployment requirements.
