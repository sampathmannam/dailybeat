# PatrolGrid native app audit

Audit date: 2026-09-02

Target: Android phone and tablet, patrol personnel and subdivision supervisors

Method: source-level native audit across accessibility, performance, appearance, Android conventions, adaptivity, and operational edge cases.

## Score

**91 / 100 — strong release candidate, pending hosted-environment and field-pilot gates.**

The mission-first structure is clear, the patrol/supervisor views are meaningfully different, tracking is bounded to an active mission, and sensitive route points are encrypted locally. Secure sign-in, server-authoritative roles, honest empty states, atomic assignment, real recorded route trails, encrypted offline actions, durable mission recovery, and permission recovery are present.

## Findings

### P0 — release blockers

No UI-only P0 defect was found. Production rollout still depends on verifying the hosted Supabase environment and completing the staged field-pilot gates.

### P1 — high priority

No open P1 item remains in the audited native flow. The six original findings were resolved with a persistent retry banner, an encrypted mutation outbox, a user-bound 24-hour encrypted mission cache, just-in-time permission recovery, operation locks, and explicit route-truncation disclosure while server evidence remains intact.

### P2 — medium priority

1. **Large-screen navigation is phone-only.** Tablets still receive a bottom navigation bar and full-width content. Use a navigation rail and constrain reading width on expanded screens.
2. **Large-font resilience needs full device-matrix evidence.** Fixed primary action heights were replaced with minimum heights and the mission card now grows with content; the remaining dense priority rows still need 200% font-scale device verification.
3. **Most PatrolGrid copy is hardcoded English.** This prevents translation and makes RTL verification incomplete despite `supportsRtl=true`. Move operational copy to string resources before multilingual rollout.
4. **Date/time display is not locale-aware.** Mission windows use a fixed `dd MMM · HH:mm` pattern. Use the device locale and subdivision timezone explicitly.
5. **Supervisor lists are capped at 100 missions with no paging state.** This is acceptable for a pilot but not a long-running subdivision history.
6. **Authentication lacks a password-recovery route.** Staff have a clear contact-admin message, but there is no safe reset/help action for managed accounts.

### P3 — polish

1. Consolidate remaining one-off status colors into semantic theme tokens.
2. Add content transition/loading treatment that respects reduced-motion settings.
3. Show a concise sync status in More: last successful sync, pending evidence count, and actionable failure state.

## Positive findings

- Material 3 components, Android back behavior, touch targets, and system insets are generally used correctly.
- Decorative icons are excluded from accessibility traversal while interactive controls retain text labels.
- Empty mission and unit states explain what happens next.
- Release builds block screenshots and obscuring overlays; cleartext traffic is disabled.
- No employee ranking, automatic misconduct finding, or route-compliance score appears in the workflow.
- Lists use lazy containers in the high-volume mission surfaces.
- Sensitive coordinate storage fails closed if Android Keystore encryption is unavailable.

## Rollout gate

Validate hosted authentication/RLS with real staging accounts, pass the automated device matrix, and complete a supervised field pilot before broad staff deployment. The operational checklist is in `docs/PATROLGRID_ROLLOUT.md`.
