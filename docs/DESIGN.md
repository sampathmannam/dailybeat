# DailyBeat product and interface direction

DailyBeat is a private, whole-day activity record. It quietly captures where the user went, turns the route into a recognizable daily card, and provides a short review step so the record is trustworthy rather than merely automatic. The product doctrine and non-goals live in [`PRODUCT.md`](../PRODUCT.md).

## Experience principles

- **The map is the proof.** Today and Days show a real map whenever reliable route points exist. Empty maps explain exactly how to start capture.
- **Automatic, then accountable.** Capture is passive, but every day has an explicit review state. Users can name a Beat, rename a stop, or hide an incorrect stop without deleting the underlying record.
- **One obvious next action.** Today leads to Review; Insights surfaces a single useful follow-up when capture or review needs attention.
- **Private by default.** Data remains on device unless the user deliberately configures cloud AI or backup. Privacy pause is prominent and automatically resumes after one hour.
- **Calm, not competitive.** DailyBeat borrows the clarity and visual energy of activity cards without importing public feeds, leaderboards, or engagement pressure.

## Visual system

| Token | Value | Use |
|-------|-------|-----|
| Canvas | `#F8FAFC` | Quiet screen background |
| Navy | `#0B2D5B` | Primary actions, text emphasis, route outline |
| Signal yellow | `#FFD60A` | Route, stops, key moments, identity |
| Off-white | `#FFFDF7` | Warm light surface and icon detail |
| Card radius | 20dp | Maps, status panels, day cards |
| Field radius | 16dp | Inputs and compact controls |

## Information architecture

- **Today** — date, route map, capture health, GPS/cloud status, activity metrics, Review My Day, moments and diary.
- **Days** — one map-led card per day with Beat title, review state, metrics and stops.
- **Insights** — 28-day private patterns, review streak, weekly movement and one actionable recommendation.
- **Settings** — capture and privacy first, then named/private places, appearance, AI and backup.
- **Review** — a focused destination opened from Today or a day card; it is intentionally not another permanent tab.

## Route language

Reliable breadcrumb segments are drawn in signal yellow with a navy edge. Stops use yellow markers with an off-white ring. Capture gaps longer than ten minutes are drawn as discontinuities/dashes and are excluded from distance totals. A base map is always used when route data exists; abstract line-only thumbnails are not an acceptable fallback for normal connected use.

## Accessibility and reliability

Status is never communicated by color alone. Primary controls retain 48dp-or-larger touch targets, support dynamic font scale, and have semantic labels used by the device test suite. Destructive actions are avoided in the review flow: hiding is reversible and does not erase captured data.
