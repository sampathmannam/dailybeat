# DailyBeat 14-day battery and capture-recall field trial

This protocol produces evidence; it does not pre-approve a battery claim. Run the standard and
Google-free builds separately on the same physical phone/route class. Do not reset Android battery
statistics and do not uninstall or clear production data.

## Design

- Days 1–3: capture-off baseline with otherwise normal phone use.
- Days 4–6: standard Google-location build, capture on.
- Days 7–9: Google-free/platform build, capture on.
- Days 10–14: repeat the weakest or noisiest condition and cover low-signal, stationary, transit,
  reboot, update and OEM battery-restriction cases.
- Take a sample after waking and before charging/sleeping. The battery comparison needs unplugged
  measurements using Android's authenticated wireless debugging on a trusted network, or manual
  on-phone readings. USB is useful for setup and diagnostics but normally charges the phone, so
  USB readings are excluded from discharge comparisons. Do not enable unauthenticated ADB TCP.
- Exclude intervals that begin or end while charging, last under one hour or over sixteen hours,
  switch build/phase/backend, cross a reboot, or include exceptional screen/navigation use.
  Mark `exclude_interval=yes` on an endpoint if charging happened between readings, even when
  both endpoints were unplugged. Endpoint readings cannot detect all intervening charging.
- Before looking at DailyBeat, independently note each stop lasting at least ten minutes. After the
  day, mark whether DailyBeat captured it and list false stops. This prevents memory from being
  rewritten by the app's result.

Use:

```sh
DAILYBEAT_CAPTURE_BACKEND=standard scripts/capture_battery_sample.sh DEVICE_SERIAL trial-01 capture_off
DAILYBEAT_CAPTURE_BACKEND=standard scripts/capture_battery_sample.sh DEVICE_SERIAL trial-01 capture_on
```

Set the phase from the actual capture switch; the collector does not change that switch or infer
its state. Set `DAILYBEAT_CAPTURE_BACKEND=platform` for the platform-only build. Record a standard
build's temporary fallback in the daily notes and exclude mixed-backend intervals from comparison.
The default backend `unknown` is deliberately reported separately. Do not switch production
packages or clear history to test another variant; use a separately named trial and the isolated
QA app when needed. Specify `DAILYBEAT_PACKAGE=com.dailybeat.app.qa` for that app.

The collector rejects emulators and missing apps, parses Android's AC/USB/wireless power flags,
and saves private raw evidence with restrictive permissions. A new CSV schema requires a new
trial directory. Raw device identifiers and reference locations must stay out of Git and public
support reports; the default trial directory is ignored by Git.

Create `stop-reference.csv` in the output directory with this header. Use neutral reference labels
where possible and keep any identifying notes private:

```csv
trial_day,reference_id,label,duration_minutes,captured,false_positive,notes
1,stop-001,Office,45,yes,no,
```

Then summarize:

```sh
python3 scripts/analyze_battery_field_trial.py deliverables/battery-field-trial-trial-01 --output summary.md
```

## Acceptance and interpretation

- Meaningful-stop recall target: at least 95% for independently recorded stops of at least ten
  minutes. Report the numerator/denominator, short stops and false positives separately.
- Compare median discharging percentage-points/hour against capture-off on matched devices and
  usage. Battery percentage is coarse; preserve the raw app-specific `batterystats` snapshots.
- Record build/version, Android build, battery-optimisation setting, permissions, temperature,
  signal/route class, screen/navigation time, reboots, capture gaps and corrective review time.
- A USB/emulator run, one day's anecdote, or the app-specific estimated power line alone is not a
  14-day field trial. Do not advertise a daily-drain number until the matched trial is complete.
- Start the 14-day clock with the first eligible physical-phone observation. Record the start/end
  dates, valid interval counts, independent stop counts, and any missing days. No observations
  means “not measured,” and false-positive app stops do not inflate the reference denominator.
