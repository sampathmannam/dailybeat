# DailyBeat Journey Beat Launcher Icon Design

## Goal

Replace the visually off-center launcher artwork with one distinctive DailyBeat mark that stays centered, legible, and recognizable under Android adaptive-icon masks and themed icons.

## Selected direction

The selected mark is **Journey Beat**: a location pin fused with a pulse line. It represents DailyBeat's two defining behaviors without text or fine detail:

- passive movement and place history;
- the rhythm of a person's day.

## Visual system

- Background: deep navy `#0B1633`.
- Primary pin: warm cream `#FFF7E8`.
- Pulse line: amber `#F4A629`.
- Focal dot: coral `#FF6B4A`.
- No lettering, gradients, shadows, photographic texture, or decorative detail.

The mark must read clearly at small launcher and settings-list sizes. The pulse line is a single continuous stroke with rounded caps. The coral dot is the only small accent.

## Adaptive-icon geometry

The Android canvas remains `108 × 108` units. Visible foreground artwork is optically centered at `(54, 54)` and constrained to a `58 × 58` optical box. No meaningful geometry may extend outside Android's central safe region.

The pin and pulse are balanced around the optical center rather than merely aligned by their bounding box. This prevents the folded-page asymmetry of the previous icon from making the mark appear shifted under circular and squircle masks.

## Android assets

The icon remains code-native and deterministic:

- `@color/ic_launcher_background` supplies the navy background.
- `ic_launcher_foreground.xml` contains the cream pin, amber pulse, and coral dot.
- `ic_launcher_monochrome.xml` contains a simplified solid pin/pulse silhouette for Android themed icons.
- Existing adaptive icon declarations continue to serve regular and round launchers.

Raster generation is intentionally excluded because the selected design is simple vector geometry and must remain sharp at every density.

## Scope boundaries

This change modifies only launcher identity assets and associated tests or release metadata. It does not alter in-app navigation icons, app behavior, data, cloud configuration, or branding copy.

The icon will be shipped with the next production-readiness release rather than published as a standalone emergency release.

## Verification

The implementation is accepted only when all of the following are verified:

1. Android resource compilation and lint pass.
2. The APK uses the expected adaptive foreground, background, round, and monochrome resources.
3. The icon is inspected under circular, squircle, rounded-square, and themed monochrome masks.
4. The release-candidate APK is installed on the connected Motorola phone.
5. Launcher and app-drawer screenshots show equal visual padding with no clipping or apparent horizontal/vertical shift.
6. The installed application still launches normally after the icon replacement.

## Production-readiness sequencing

Icon implementation is the first isolated deliverable. Reliability work follows in a separate design and implementation cycle covering the confirmed intermittent rendering crash, MapLibre memory pressure, observability, and budget-capped DeepSeek validation. Those concerns must not be hidden inside the visual asset change.
