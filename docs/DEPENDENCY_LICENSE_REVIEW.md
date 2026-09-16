# Dependency licence declarations — 16 September 2026

DailyBeat original source is GPL-3.0-only by owner approval. That licence does not relicense third-party material.

This inventory reads the POMs for the exact runtime coordinates exported by the Gradle inventory task
at `6504e58`. It is a declaration inventory, **not completed legal/redistribution clearance**.
See [the coordinate-level evidence](dependency-license-inventory.json).

| Runtime configuration | Modules | Apache 2.0 declarations | BSD declarations | Android SDK licence declarations |
| --- | ---: | ---: | ---: | ---: |
| Standard | 93 | 87 | 2 | 4 |
| Google-free | 89 | 87 | 2 | 0 |

`com.google.guava:listenablefuture:1.0` inherits its declaration from the inspected
`com.google.guava:guava-parent:26.0-android` POM. The other modules declare licences directly.
The inventory normalises the several spellings of Apache 2.0 for the table above only; raw names and
URLs are retained in the JSON. MapLibre's two BSD declarations link to BSD-2-Clause.

The four Android SDK-licensed modules are Google Play Services location 21.3.0, base 18.5.0,
basement 18.4.0 and tasks 18.2.0. They are excluded from the Google-free configuration. Their POMs
point to [the Android SDK terms](https://developer.android.com/studio/terms.html), not GPL.
Do not publish the standard APK as cleared GPL/FLOSS distribution without a separate compatibility
review. No linking exception was approved or added. The production publisher has not been switched
silently to a different location backend.

Still required before distribution:

- Verify bundled native subcomponents and their notices, especially inside MapLibre's AAR.
- Verify app font/image/icon origins, generated assets and any copied code.
- Bundle applicable third-party notices and make the app's own licence/source notice accessible.
- Review GPL source-delivery obligations and store/signing requirements for the exact released APK.
- Regenerate the inventory after dependency changes; POM labels alone are not licence compatibility proof.

The Google-free build is the FLOSS distribution candidate, not automatic F-Droid approval.
