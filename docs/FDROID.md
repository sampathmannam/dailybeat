# F-Droid distribution

DailyBeat's owner requests inclusion in the official F-Droid repository. This document describes
the distribution candidate; a recipe and passing upstream checks do not constitute F-Droid approval.

## Build

Use OpenJDK 21, Android SDK platform 36, build tools 35.0.0 and the checked-in Gradle 8.11.1 wrapper.
The Android source and original vector artwork are GPL-3.0-only. Dependencies retain their licences.

```sh
./android/gradlew -p android assembleRelease testReleaseUnitTest lintRelease \
  verifyGoogleFreeDependencies -PdailybeatFoss=true -PdailybeatStore=true \
  -PdailybeatUnsigned=true --no-daemon
```

The unsigned artifact is `android/app/build/outputs/apk/release/app-release-unsigned.apk`.
No signing key, account, API key or managed-backend environment variable is needed. Store mode
rejects a Google-backed build and ignores `SUPABASE_URL` / `SUPABASE_ANON_KEY` properties and
environment variables. A release-only test verifies this even with deliberately populated CI values.
The public map catalog references immutable data assets; compiling the app does not download a map.

`fdroid/com.dailybeat.app.yml` is the recipe template. The submission in `fdroiddata` must replace
its tag with the exact release commit hash. The F-Droid recipe removes the unused `src/gms` tree,
the separate GMS dependency script and Android-test map fixtures. These files cannot affect the
store APK. There are no broad scanner exceptions, vendored executable libraries or downloaded code.
The Gradle wrapper is verified by its checksum and CI; Android and Maven toolchains are the standard
F-Droid-supported tools.

## Signing and updates

The existing stable Obtainium channel continues to publish `DailyBeat-vVERSION.apk`. The store
candidate uses a separate GitHub prerelease tag, `fdroid-vVERSION`, with
`DailyBeat-FDroid-vVERSION.apk`. This supplies the signed reference for F-Droid reproducible builds
without making existing Obtainium clients choose between two APKs in a stable release.

Both use the existing certificate:
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`.
The F-Droid recipe requires that certificate and a matching rebuild. Do not switch to F-Droid's
own signing key merely to bypass a reproducibility failure: diagnose the difference first. The
GitHub release pipeline waits for required checks, builds the candidate only after the stable
publisher succeeds, verifies its certificate and refuses asset replacement. GitHub release
immutability is enabled. Tags matching stable `vMAJOR.MINOR.PATCH` drive the proposed auto-update rule.

CI builds the unsigned store APK twice in independent checkout paths, removing exactly the files
removed by the F-Droid recipe for the second build, and compares the APK bytes. It also runs the
actual F-Droid source and binary scanners. F-Droid's own rebuild remains authoritative.

## Feature and network disclosures

- The store build uses Android's platform location backend. It does not ship Google Play Services,
  Firebase, advertising or analytics SDKs. It retains continuous location capture instead of Google's
  activity-transition sleep mechanism; physical battery performance needs separate measurement.
- Notes, local drafts, search, review, map viewing and PDF/ZIP sharing require no account. Managed
  cloud backup is not configured in the store APK; do not advertise it as an available store feature.
- Cloud drafting is optional and off by default. Proprietary AI presets remain available, as does
  a configurable compatible endpoint. `NonFreeNet` is disclosed for these presets.
- Online maps remain on by default, as in the stable app. Providers receive IP addresses and viewed
  areas. `Tracking` is disclosed conservatively for review for this default network behavior; there
  is no analytics SDK. Users can turn it off, and map requests then stop.
- Optional, explicitly requested Tamil Nadu data downloads use GitHub Releases. `TetheredNet` is
  disclosed for that package source. Map data, local styles, fonts and sprites are data resources,
  not executable code. See [offline map provenance](OFFLINE_MAPS.md).
- Address lookup is independently disabled unless configured. Android speech recognition uses the
  user's device provider. See [privacy information](../PRIVACY.md).

F-Droid reviewers determine the final anti-feature labels. Disclose behavior rather than suppressing
scanner findings or hiding network dependencies.

## Licences, assets and listing

[The Maven declaration inventory](dependency-license-inventory.json) identifies exact runtime
coordinates. [Native notice provenance](native-license-provenance.json) pins the upstream notice
sources and SHA-256 hashes. The APK's **Licences & source** screen includes the original GPL text,
Apache 2.0 text, MapLibre Native Android 11.8.0's upstream notices and additional PMTiles,
unordered_dense, ICU and nunicode notices. SQLite's source is public domain. RapidJSON's non-runtime
JSON-checker test material is not linked into the app; its runtime headers use MIT.

The app uses original vector icon sources and system typography, not downloaded application fonts.
Compose/Material icons are Apache-2.0 resources. The separate Tamil Nadu package carries its own
OpenStreetMap, boundary, style, sprite and font licences. Store screenshots must use synthetic data.
English listing text, screenshots, the icon and changelogs are maintained under
`fastlane/metadata/android/en-US/`. The 512-pixel store icon is rasterized from
`docs/assets/store-icon.svg`, derived from the original Android vector artwork.
Screenshots were captured on 2026-09-20 from the store-mode QA build on a disposable Android
emulator; journal entries are fictional, with no personal location or diary data.

The legacy Google-backed APK is not the F-Droid candidate. Its proprietary SDK declarations are
separately documented and must not be presented as a wholly FLOSS distribution.

## Submission process

Follow [F-Droid's inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) and
[contribution instructions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).
Submit one metadata file through a public `fdroiddata` fork, with the exact stable source commit,
reference APK URL, signing certificate and current Fastlane listing. Run metadata lint, source/APK
scanners, reproducibility checks and the upstream CI. If GitLab blocks a fork's runners behind
account verification, request a maintainer-run pipeline as the F-Droid MR template instructs.
Acceptance, signing verification and final publication require F-Droid's review and build cycle.
