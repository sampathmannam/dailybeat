# PatrolGrid

PatrolGrid is an Android patrol-coordination app for subdivision supervisors and field patrol personnel. It turns informal location updates into clear patrol missions while preserving field judgment and explicit tracking boundaries.

## Current build

The current PatrolGrid implementation includes:

- A shared Android app with supervisor and patrol-personnel roles.
- A mission-first **Patrol Control** view for active patrols, exceptions, upcoming duties, and contextual review.
- A supervisor assignment sheet for selecting a route plan, patrol unit, priority locations, duty window, and suggested-route versus flexible-area guidance.
- A **My Patrol** view for duty briefing, priority locations, route context, observations, operational deviations, and patrol completion. Appearance follows the device light/dark setting.
- Location tracking that is off by default, starts only with an active patrol, stores AES-256-GCM-encrypted local route evidence against the mission, and stops when the patrol ends.
- Authenticated Supabase synchronization with server-enforced subdivision and role authorization, offline evidence queues, selected-mission detail, and supervisor review outcomes.
- Geographic planned-versus-recorded routes rendered with open-source MapLibre and keyless OpenFreeMap/OpenStreetMap data, plus a tile-free evidence fallback.
- A fixed 365-day post-closure evidence-retention clock with local and server cleanup controls, finite reviewed legal holds, and auditable deletion operations.
- Human-review outcomes instead of employee scores or automatic misconduct findings.
- Offline-ready encrypted mission state, Room persistence for route points, adaptive Material 3 navigation, and accessible light/dark themes.

The local role switch and sample missions exist only in debug builds. A release build fails closed unless its production Supabase identity and approved privacy-policy inputs match the source-controlled deployment record. See [rollout readiness](docs/PATROLGRID_ROLLOUT_READINESS.md) before using staff or operational data.

## Build

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew testDebugUnitTest assembleDebug --dependency-verification strict
```

CI and release verification use strict dependency locks and checksum metadata; local release work must follow [docs/RELEASE.md](docs/RELEASE.md), not the debug command above.

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Product principles

- Mission before movement.
- Exceptions before surveillance.
- Evidence always includes operational context.
- Routes support field judgment; they do not replace it.
- Tracking is visible, explicit, and bounded to duty.

See [PRODUCT.md](PRODUCT.md) for the product definition, [docs/DESIGN.md](docs/DESIGN.md) for the UI system, and [docs/SECURITY.md](docs/SECURITY.md) for implemented controls and pre-deployment requirements.
