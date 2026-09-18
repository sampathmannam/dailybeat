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
- Take a sample after waking and before charging/sleeping. Exclude intervals that begin or end while
  charging, last under one hour, switch build/phase, or include exceptional screen/navigation use.
- Before looking at DailyBeat, independently note each stop lasting at least ten minutes. After the
  day, mark whether DailyBeat captured it and list false stops. This prevents memory from being
  rewritten by the app's result.

Use:

```sh
scripts/capture_battery_sample.sh DEVICE_SERIAL trial-01 capture_off
scripts/capture_battery_sample.sh DEVICE_SERIAL trial-01 capture_on
```

Create `stop-reference.csv` in the output directory with this header:

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
