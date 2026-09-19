# Adaptive battery capture

DailyBeat records a whole day without treating a stationary phone like an active navigation
session. It uses a small state machine rather than a permanent high-frequency GPS request.

| State | Runtime work | Capture behaviour |
| --- | --- | --- |
| Watching | Google Play activity transitions | Watches only for moving/still transitions; no location foreground service runs. |
| Moving | Location foreground service, balanced power | Requests a route point at 45 seconds (including stationary fixes for dwell confirmation), delivered in up-to-two-minute batches. |
| Settling | Location foreground service, balanced power | After `STILL`, backs off to two minutes with at-most-two-minute delivery batches. Waits at least ten minutes and requires an observed eight-minute stay before stopping. |

## Reliability boundaries

- Whole-day background restart on a movement event requires Android's **Allow all the time**
  location grant. Without it, a service started from the visible app keeps collecting after the app is backgrounded,
  but it must not sleep and attempt a later background restart. Opening the app restores capture.
- The watcher is deliberately coordinate-free. Location stays within the existing local capture
  path and private-place exclusion rules remain unchanged.
- A delayed stillness worker is a safety net, not a precise clock. Android may run it later under
  Doze; that only keeps the settling request alive a little longer and never drops route data.
- If a handset never delivers a stillness transition, eight minutes without meaningful movement
  stops the active service only when a motion watcher is confirmed armed, background location is
  granted, and a confirmed stay is checkpointed. FOSS and foreground-only grants do not enter sleep.
- Battery sleep persists a suspension boundary. The next fix starts a new observed segment; the
  unobserved interval is never labelled as continuous travel.
- Pause expiry and boot rearm the watcher and show a tap-to-resume notification when recording is
  stopped. Notifications require the user’s notification permission. WorkManager does not launch
  an unsupported background foreground service.

## Route evidence and writes

Every accepted location continues into visit detection, so dwell and transit logic see all fused
fixes in a batch. Database persistence keeps the first point, meaningful 75-metre displacement,
material accuracy recovery, and one point every five minutes. A raw batch first enters a durable Room inbox. Each accepted fix commits its visit, timeline entry,
optional route point, checkpoint and acknowledgement together. Failed transactions remain queued
for a WorkManager retry or the next capture start. Erase and restore invalidate stale callbacks.

## User-facing battery policy

Battery optimisation remains on by default. The app should offer an OEM's “Unrestricted” setting
only after documented repeated capture gaps, never as a blanket onboarding demand. Physical-activity
permission enables the adaptive watcher; without it, DailyBeat retains the baseline moving profile
so location capture remains functional rather than silently becoming incomplete.
