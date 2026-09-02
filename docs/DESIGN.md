# PatrolGrid Android design

PatrolGrid uses a mission-first, exception-first information architecture. The supervisor experience leads with responsibility and context; the patrol-personnel experience leads with the active briefing and priority locations. Maps support those decisions rather than becoming a surveillance-first dashboard.

## Visual system

| Token | Value | Use |
|---|---:|---|
| Ink | `#0F172A` | Primary text and near-black foundation |
| Navy | `#1E3A5F` | Institutional actions and supervisor navigation |
| Navy Soft | `#2D4A6F` | Selected and elevated operational surfaces |
| Gold | `#E8A317` | Patrol actions, priority points, brand accent |
| Gold Soft | `#F5D78E` | Gentle emphasis on light surfaces |
| Canvas | `#F8F7F4` | Supervisor/day background |
| Night Canvas | `#071827` | Patrol/night background |
| Success | `#16A34A` | Completed evidence only |
| Error | `#DC2626` | Destructive or failed system states only |

Use system sans typography, minimum 48 dp interactive targets, non-color status labels, and compact 12–16 dp surface radii. Avoid decorative gradients, glass effects, metric dashboards, pill clutter, and giant score numerals.

## Supervisor — Patrol Control

1. Active / Needs review / Upcoming mission states.
2. Needs-attention items before general mission lists.
3. Active patrol cards with duty window, unit, personnel count, plain-language status, and context.
4. Compact selected-mission map showing zone, suggested route, recorded path, and priority points.
5. Assignment as the primary creation action.

The view never exposes employee rankings. Missing GPS, incomplete priorities, and operational deviations create a request for context, not an automatic failure.

## Patrol personnel — My Patrol

1. Mission briefing with duty window and explicit tracking state.
2. Priority locations in visit order with visited, current, and remaining states.
3. Compact route context and a visible offline-ready state.
4. Observation and deviation actions available during an active patrol.
5. Start and end actions that control the bounded location session.

PatrolGrid follows the device's system appearance instead of coupling light or dark mode to a staff role. Staff can use the device's dark appearance for night duty without forcing every patrol-personnel session into night colors. Suggested routes may change for safety or operational need; the interface explains this next to the route instead of hiding it in policy text.

## Product boundaries

- No continuous location collection outside an active patrol.
- No automatic misconduct conclusion from a GPS gap.
- No route-compliance score or leaderboard.
- No hidden camera, microphone, biometric, or call-log capture.
- Production roles must come from authenticated subdivision access; the local role switch exists only for development preview.
