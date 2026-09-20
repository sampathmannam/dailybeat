# Dependency licence review — F-Droid candidate 4.3.1

DailyBeat original source is GPL-3.0-only by owner approval. That licence does not relicense third-party material.

This inventory reads the POMs for the exact runtime coordinates exported by the Gradle inventory task
at `6504e58`. Both runtime coordinate sets were re-resolved for 4.3.1 on 20 September 2026
and remain unchanged. POM declarations alone are not a legal opinion or F-Droid approval.
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

The F-Droid candidate closes the previously outstanding packaging checks:

- The only packaged native libraries are MapLibre and AndroidX graphics-path. MapLibre Native Android
  11.8.0's upstream notice bundle is included verbatim, supplemented with its pinned PMTiles,
  unordered_dense, ICU and nunicode licences and LLVM libc++ runtime notices. AndroidX graphics-path
  source uses Apache 2.0. SQLite is public domain. Sources/hashes are recorded in
  [native-license-provenance.json](native-license-provenance.json).
- The upstream RapidJSON notice describes source-only JSON-checker fixtures; those are not linked
  into the runtime. Runtime RapidJSON headers are MIT. Retaining the upstream notice does not add
  those fixtures to the app.
- App artwork is source-form vector XML under the project's GPL licence; there are no bundled app
  fonts or proprietary image packs. Compose/Material icons use Apache 2.0. Separate map resources
  retain their own notices and pinned build provenance in [OFFLINE_MAPS.md](OFFLINE_MAPS.md).
- Settings → **Licences & source** exposes the repository GPL, Apache and complete native notices.
  APK verification checks the actual packaged licence bytes after resource shrinking.
- The tagged public source, unsigned build recipe and public dependency repositories permit source
  rebuilds without private credentials. A signed reference and required permanent certificate are
  supplied for F-Droid's own reproducibility verification. See [FDROID.md](FDROID.md).
- F-Droid's actual source and binary scanners are release gates; they complement, not replace,
  maintainers' licence review. Regenerate the inventory whenever dependencies change.

The new Error Prone 2.23.0 dependency is Apache-2.0 compile-time annotation support for R8/Tink;
it does not add a runtime module. The legacy Google-backed distribution remains outside the F-Droid
submission and is not represented as wholly FLOSS. F-Droid acceptance remains a maintainer decision.
