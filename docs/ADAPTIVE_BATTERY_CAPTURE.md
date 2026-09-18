# Adaptive battery capture

DailyBeat records a whole day without treating a stationary phone like an active navigation
session. It uses a small state machine rather than a permanent high-frequency GPS request.

| State | Runtime work | Capture behaviour |
| --- | --- | --- |
| Watching | Google Play activity transitions | Watches only for moving/still transitions; no location foreground service runs. |
| Moving | Location foreground service, balanced power | Requests a route point at 45 seconds / 75 metres, delivered in up-to-two-minute batches. |
| Settling | Location foreground service, balanced power | After `STILL`, backs off to two minutes / 100 metres and waits five minutes before stopping. |

## Reliability boundaries

- Whole-day background restart on a movement event requires Android's **Allow all the time**
  location grant. Without it, DailyBeat still captures while open but must not create a location
  foreground service from a background receiver on Android 14+.
- The watcher is deliberately coordinate-free. Location stays within the existing local capture
  path and private-place exclusion rules remain unchanged.
- A delayed stillness worker is a safety net, not a precise clock. Android may run it later under
  Doze; that only keeps the settling request alive a little longer and never drops route data.
- If a handset never delivers a stillness transition, eight minutes without meaningful movement
  stops the active service only when a motion watcher is confirmed armed.

## Route evidence and writes

Every accepted location continues into visit detection, so dwell and transit logic see all fused
fixes in a batch. Database persistence keeps the first point, meaningful 75-metre displacement,
material accuracy recovery, and one point every five minutes. A whole fused batch is written in one
Room transaction rather than one coroutine and insert for each callback.

## User-facing battery policy

Battery optimisation remains on by default. The app should offer an OEM's “Unrestricted” setting
only after documented repeated capture gaps, never as a blanket onboarding demand. Physical-activity
permission enables the adaptive watcher; without it, DailyBeat retains the baseline moving profile
so location capture remains functional rather than silently becoming incomplete.
