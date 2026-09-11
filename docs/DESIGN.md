# DailyBeat experience direction — v3.9

DailyBeat is a private **journey ledger**: it quietly records the day, shows the map as evidence, and makes uncertainty easy to repair. The interface should feel like dependable field equipment—calm while capture is healthy, direct when attention is needed, and never gamified. The product doctrine and non-goals live in [`PRODUCT.md`](../PRODUCT.md).

The editable design handoff is in [Daily Beat — Product Experience v3.9](https://www.figma.com/design/fFqXwpmbBjhBTC0CyFxja3). It uses the official Material 3 Design Kit as its component reference; the shipped Compose UI remains the source of truth until proposed Figma frames have passed implementation review.

## Experience principles

1. **The map is the proof.** Today and Days lead with a real map whenever reliable route points exist. Empty maps explain how capture starts instead of simulating a route.
2. **Trust before delight.** Recent-fix age, capture state, estimates, and gaps are shown before the app claims that a day is complete.
3. **One obvious next action.** Today leads to Review. Add Moment and Diary remain available without competing with that action.
4. **Automatic, then accountable.** Capture is passive; review is explicit, fast, and reversible. Users can name a Beat, correct a stop, or hide an incorrect stop without erasing raw history.
5. **Private by default.** Location and diary data remain local unless the user deliberately invokes a configured backup, AI, or share action.

## Visual system

| Token | Value | Role |
|---|---:|---|
| Canvas | `#F8FAFC` | Quiet screen background |
| Navy | `#0B2D5B` | Primary actions, emphasis, route edge |
| Ink | `#0B1B33` | High-contrast text |
| Signal yellow | `#FFD60A` | Route, stops, key moments, identity |
| Off-white | `#FFFDF7` | Warm light surface and icon detail |
| Large radius | `20dp` | Maps and the single dominant day object |
| Medium radius | `16dp` | Controls and status surfaces |

Typography uses the Android system family and Material 3 roles. Screen titles use `headlineLarge`; primary day objects use `titleLarge`; supporting status copy uses `bodyMedium`; compact labels use `labelSmall` or `labelMedium`. Avoid custom type for operational copy: legibility, script coverage, and font scaling take precedence.

## Information architecture

- **Today** — date, route proof, capture overview, one Beat summary, Review My Day, moments, diary.
- **Days** — map-led daily records with Beat title, review state, metrics, and stops.
- **Insights** — 28-day private patterns, review rhythm, weekly movement, and one useful recommendation.
- **Settings** — capture and privacy first, then places, appearance, AI, and backup.
- **Review** — a focused flow opened from Today or a day; it is intentionally not a permanent tab.

On compact widths, these four top-level destinations use bottom navigation. At `600dp` and wider they move to a navigation rail, leaving vertical room for the day record. Content stays centered and readable instead of stretching across a tablet.

## Today composition

Today has four visual levels:

1. Date and purpose.
2. Route map or an honest capture-empty state.
3. A compact capture overview containing health, GPS, and optional cloud readiness.
4. One Beat summary containing title, review state, distance, tracked time, and stops.

Only Review My Day receives primary-action styling. Repeated metric cards are avoided because distance, time, and stops describe one object—the Beat—and should scan as one unit.

## Route language

Reliable breadcrumb segments use signal yellow with a navy edge. Stops use yellow markers with an off-white ring. Capture gaps longer than ten minutes appear as discontinuities and are excluded from distance totals. When route data exists, use a base map; an abstract line-only thumbnail is not an acceptable connected-state fallback.

## Interaction and motion

Motion explains continuity: sheet entry, map selection, and review progression may use short Material transitions. Do not animate continuously during passive capture, celebrate distance, or add decorative parallax. Respect the system animator scale and keep the core review flow usable with motion disabled.

## Accessibility and reliability

- Never communicate capture or review state by color alone.
- Keep interactive targets at least `48dp` and preserve predictable Android Back behavior.
- Support TalkBack, dark theme, `200%` font scaling, edge-to-edge insets, and compact/expanded widths.
- Let status labels truncate safely rather than overlap; keep essential explanations multiline.
- Errors say what happened and provide a recovery action without exposing exception text.
- Hiding or correcting inferred data is reversible and does not silently delete captured history.

## Anti-patterns

Do not turn DailyBeat into a beige journal, a card-grid dashboard, a social feed, a sports tracker, a surveillance product, or a technical diagnostics console. Avoid nested cards, repeated pill shapes, decorative gradients, emoji placeholders, fake precision, and engagement mechanics.
