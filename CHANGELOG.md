# Changelog

## 4.0.1 — 2026-09-13

### Redesigned
- **Days cards now read as a focused journey summary.** Each card prioritizes the date, review
  state, route map, distance, tracked time, stops, and the action to inspect the day.
- **Generated diary prose is no longer duplicated inside the card.** Legacy midday-pulse and diary
  preview text have been removed from the Days list; the complete diary remains available in its
  dedicated day view.

### Hardened
- **Release and dependency checks fail closed.** The protected release commit must pass build,
  offline and live instrumentation, cloud-backup, release-policy, CodeQL, and open-source security
  gates before the permanent signing key can publish an APK.
- **Production safeguards cover secrets, networking, backups, and Android components.** Runtime
  provider keys remain device-encrypted and absent from source and release artifacts, owner-only
  backup policies remain enforced, and exported Android components stay explicitly constrained.

### Verification
- Added regression coverage for the simplified Days-card information hierarchy.
- Re-ran the phone UI flow and the complete protected CI/security matrix before release.

## 4.0.0 — 2026-09-12

### Redesigned
- **DailyBeat is now explicitly phone-only.** Today, Days, Insights, and Settings keep the same
  bottom navigation in portrait and landscape instead of switching to a tablet navigation rail.
  Primary actions stay predictable and within comfortable thumb reach.
- **The DailyBeat Penpot v4 architecture is the design source.** It documents the complete journey
  from capture to evidence, review, diary, and private learning, alongside all nine production phone
  states and the Android interaction contract.

### Improved
- **Saved moments are visible on Today immediately.** The newest local notes appear in chronological
  context instead of disappearing after save.
- **Capture status tells the truth calmly.** A deliberate privacy pause is distinct from capture
  failure, capture gaps are disclosed and excluded from distance, and the last GPS fix includes
  freshness as well as accuracy.
- **Settings follows the privacy contract.** Capture and named places come before appearance and
  connected cloud services.
- **Phone content remains readable.** Screen content is centered where a landscape window is wider,
  while navigation and interaction stay phone-native.

### Verification
- Added policy coverage that prevents the navigation rail and width-triggered tablet shell from
  returning.

## 3.9.0 — 2026-09-12

### Redesigned
- **DailyBeat now adapts cleanly from phones to larger screens.** Compact devices keep the familiar
  bottom navigation; displays 600dp and wider use a navigation rail, and Today stays centered at a
  readable width instead of stretching across a tablet.
- **Today reads as one coherent journey ledger.** Capture health, GPS, and Cloud AI readiness now
  share one operational overview. Distance, tracked time, and stops share one Beat summary instead
  of competing metric cards, while **Review my day** remains the single primary completion action.
- **The visual hierarchy is calmer and more accessible.** Screen titles expose heading semantics,
  primary and secondary actions retain 48dp minimum targets, edge-to-edge insets behave consistently,
  and the navy/yellow identity is preserved across compact and expanded layouts.

### Fixed
- **Onboarding no longer opens a redundant permission activity.** When location and notification
  access are already granted, DailyBeat proceeds directly to Today and applies capture settings.
- **The onboarding device test no longer races the final frame.** It waits for Today to become
  visibly settled, keeping both connected and offline emulator gates deterministic.

### Documentation
- Replaced the loose visual notes with a v3.9 journey-ledger design direction covering information
  architecture, adaptive behavior, tokens, accessibility, motion, and explicit UI anti-patterns.

## 3.8.3 — 2026-09-11

### Changed
- **Days no longer labels every day "Needs review".** The badge appeared on every past day and on
  today regardless of whether anything needed attention. It now shows only when a day has a real
  capture gap or a stop you flagged, and "Complete" once you finish a Beat; an ordinary captured
  day carries no badge.

### Fixed
- **"Use current location" can no longer hang.** If a GPS fix never arrived — location off,
  indoors, or a wedged provider — the button's spinner ran forever and, because that same state
  guarded the button, it could never be tapped again. The fix now times out after 15 seconds and
  shows the retry message instead.
- **Cloud backup no longer fails with a bare error when a diary is written in a regional script.**
  The client's "too large" guard counted characters while the server counts bytes; a Tamil diary
  (three bytes per character) could slip past the client and be rejected by the server with an
  unexplained failure. Both now measure the same 12 MiB in bytes, so an over-size backup is caught
  on the device with a message that says what to do.

### Internal
- Removed an unused capture-health threshold constant left over from the old alarming status.

## 3.8.2 — 2026-09-10

### Fixed
- **"Private zone" now actually works.** The switch was saved and shown but never consulted, so a
  place marked private was still described to the cloud AI provider, still written into shared
  exports, and still looked up by name against OpenStreetMap. Stays inside a private zone are now
  excluded from every outbound path, and their coordinates are never sent to the geocoder at all.
- **"Hide stop" now applies beyond the screen.** A stop hidden in Review my day disappeared from
  the day card but was still sent to the cloud provider and included in the backup. It is now
  excluded from reports and exports as well.
- **Daily, midday and weekly reports no longer include raw GPS coordinates.** Reports identify
  places by name and time; the roughly 11-metre positions that used to accompany every stay served
  no purpose in the generated diary.
- The weekly rollup counts only the stays it actually sends, so the number no longer reveals how
  many were withheld.

### Changed
- **Add a place by its current location, not by typing coordinates.** The add-place form asked
  for latitude and longitude — numbers nobody carries in their head. A **Use current location**
  button now captures one GPS fix and fills the spot; Add place stays disabled until there is both
  a name and a captured location.
- **The 1 PM automatic midday pulse is gone.** It no longer runs, the Settings toggle is removed,
  and any pulse a previous version had scheduled is cancelled on first launch. The evening report
  is unchanged.
- **Insights is interactive.** Tap any bar to see that day's date and distance above the chart;
  each bar is reachable and announced with TalkBack. It starts on the most recent day.
- **Tapping a day opens its map.** A day in the Days list now opens that day's journey to look at,
  instead of dropping you into the rename/hide/complete flow. Review my day stays on Today.
- **The Today capture status no longer alarms.** The red "route has not updated recently — open
  Settings to troubleshoot" message is removed. A long quiet stretch now reads calmly: "No recent
  movement — DailyBeat is still watching and keeps everything it captured." Only capture being
  switched off is flagged as something to act on.

### Polished
- **Numbers read the same on every screen.** The same day used to show "~4 km · 3h 34m" on Today
  and "4.0 km · 3 h 34 min" on Days; a short day was "50 m", "0.1 km" or "—" depending on where
  you looked. Distance, duration, times and counts now come from one place. The "~" that marks an
  estimated distance is explained, and no longer disappears on the Days card.
- **Real icons instead of emoji** on the onboarding steps, the empty states and event notes, and
  the app's own mark in the status-bar notification instead of a generic system glyph.
- **Onboarding tells you what Android is about to ask.** Both permissions are named with one
  reason each, and the two-step "While using the app → Allow all the time" location flow is
  spelled out before it starts. Rotating the phone mid-onboarding no longer restarts it.
- **Contrast fixed where it failed.** Text-field borders were nearly invisible; stop markers had
  no visible edge on the light map; the Insights chart bars and route-replay progress were yellow
  on white. Hidden stops now say "Hidden from this Beat" instead of relying on a faint tint, and
  the cloud-provider choice shows a check mark rather than a shade.
- **Dark theme and large text.** Cold starts in dark mode no longer flash a white screen; the
  system bars follow the appearance you chose in Settings; the bottom bar keeps one line per tab
  at 200% font size; the splash icon's ring is even.
- **Clearer feedback.** A failure loading Days now shows an error with Try again instead of "No
  days captured yet"; saving a voice note confirms it; error messages say what went wrong in
  plain words rather than showing raw technical text; the keyboard no longer covers the field
  you are typing into on Settings and Review; the full map titles a past day by its date.
- Wording: "Cloud AI" throughout, "Review my day" for both the button and its screen, and the
  route-recording switch no longer shows internal sampling parameters.

### Notes
- Private zones and hidden stops remain in your own cloud backup. Dropping them would lose the
  private flag itself on a restore and silently re-expose those places.
- No screen, layout or navigation changed. The Settings wording for a private zone now describes
  what it does.

### Removed
- The dead local-model era: the Aider PowerShell tooling, an unused load-test harness, Cursor
  editor configuration, completed design plans, the v1 UI preview mock, and `PLAN.md` — a spec for
  an offline fine-tuned model that the cloud-only app has not matched for many versions.
- Unreachable app code, each verified to have no caller: a superseded visit card, an unused diary
  formatter, 34 dormant DSR query methods that were still being packaged into the APK, several
  orphaned repository and DAO methods, and 8 strings left over from the move to four tabs.
- The dormant DSR tables, entities and migration chain are untouched, so existing records still
  survive every upgrade.

### Security
- Gradle dependency verification now pins every build dependency by SHA-256, with a policy test
  that fails the release gate if signature verification or a trusted-key bypass is ever introduced.
- Release tags are immutable and the branch ruleset no longer refers to an unrelated product.
- An explicit network security configuration denies cleartext traffic instead of relying on the
  implicit platform default.
- The cloud and backup HTTP clients refuse redirects, so a custom authentication header cannot be
  replayed to a redirect target, and the backup client now has bounded timeouts rather than none.
- Release builds strip `android.util.Log` calls; CodeQL now also analyses the Python release
  tooling; Dependabot version updates are enabled; and CI removes the decoded signing key when the
  release job finishes.

## 3.8.1 — 2026-09-10

### Added
- **Visible theme control.** Settings now starts with an Appearance selector for System, Light,
  and Dark modes. The choice persists across restarts and is included in cloud backup snapshots.
- **Route replay.** The full map can animate the yellow journey from its first captured point to
  its last, with a moving position marker and accessible play, pause, and resume controls.

### Fixed
- Theme changes now update the app and Android system-bar contrast immediately.
- A slow Settings refresh can no longer undo a theme choice made while the screen is loading.
- Cloud AI errors remain visible when diary generation is attempted with the keyboard open.
- Capture gaps remain safely excluded from measured distance, but no longer create review prompts
  on Today, Days, Review, or Insights. DailyBeat simply keeps the reliable points it captured.

## 3.8.0 — 2026-09-10

### Added
- **Whole-day Beats.** Every captured day now has a name and an explicit review state. Review My
  Day lets the user rename stops, reversibly hide an incorrect stop, restore it, and mark the Beat
  complete without deleting the underlying capture record.
- **Private Insights.** A new 28-day view shows weekly movement, review streak, a seven-day chart,
  recurring places, and one deterministic next action based on capture health or unfinished review.
- **Reliable GPS breadcrumbs.** High-frequency route points are stored separately from dwell
  visits. Invalid, mock, inaccurate, and physically impossible fixes are rejected; gaps longer than
  ten minutes are disclosed and excluded from distance totals.
- **Privacy pause.** Capture can be paused for one hour and automatically resumes through
  WorkManager, including after process recreation.

### Improved
- **Map-first experience.** Today puts the route directly below the date, Days uses one polished
  map-led card per day, and the full map keeps a visible yellow route fallback until its interactive
  street tiles are genuinely rendered.
- **Clearer app structure.** The permanent destinations are Today, Days, Insights, and Settings.
  Diary and Review stay in the daily flow instead of competing for navigation space.
- **Production UI.** New navy/yellow identity, launcher icon, dark theme, aligned GPS/cloud status
  panels, stronger hierarchy, accessible labels, and compact distance formatting.
- **Cloud backup schema 2.** Breadcrumbs, Beat reviews, reversible review fields, and private-place
  flags now round-trip while schema-1 backups remain restorable.

### Reliability and tests
- Database schema 8 uses additive migrations only and retains dormant legacy DSR data.
- Capture shutdown waits for pending breadcrumb writes and never restores an older fix over a newer
  callback.
- Expanded unit, migration, navigation, privacy, review, insight, and end-to-end device coverage.
- Locust synthetic cloud-report load gate: 6,823 requests, zero failures, approximately 230 req/s,
  with a 2 ms mean and 11 ms p99 on the local compatible test endpoint.

## 3.7.2 — 2026-09-09

### Improved
- **Today and Feed now show real OpenStreetMap context** instead of an abstract route sketch.
  Travel is drawn in DailyBeat yellow with a navy casing and clear stay markers; Today keeps a
  stable snapshot card and opens the existing interactive full map on demand.
- **GPS tracking and Cloud AI status indicators now align consistently** with matching icon slots,
  height, spacing, and vertical position.

### Fixed
- Feed map verification now scrolls to the map itself, covering compact phone viewports without
  depending on how the clickable day card merges accessibility semantics.

## 3.7.1 — 2026-09-09

### Fixed
- **Existing installs no longer fail during the schema-6 to schema-7 upgrade.** Early versions
  could reach schema 6 without the diary-date index because they started after the older migration
  that created it. The final migration now repairs the missing index idempotently while preserving
  every diary row, and a regression test reproduces the exact released-device database shape.

## 3.7.0 — 2026-09-07

### Added
- **A redesigned adaptive “Journey Ledger” app icon** combines the daily record, mapped route,
  and destination in one small-size-safe mark, with Android monochrome/themed-icon support.
- **Daily journey feed.** The History tab is now a feed with one card per day: the day's route
  drawn from its GPS track, distance travelled, time out, stop count, and every stay listed as
  name, arrival time, and duration ("Rasipuram Police Station · 08:00 · 40 min"). Tapping a day
  opens its diary. Routes are vector-drawn rather than embedding a map view per card.
- **Places are named by the map.** Reverse geocoding now requests name details at building zoom
  and prefers the feature's own name over the street it sits on, so a stay at a police station is
  labelled as that station. Names are cached with the address (schema 6).

### Removed
- **Call-log capture.** The permission, worker, Settings toggle, synthetic call events, and the
  call wording in the AI prompts are gone. Restoring an older cloud backup still works.

### Fixed
- **Voice notes no longer depend on a successful cloud round trip.** The recognized transcript is
  always preserved as the source event; cloud metadata extraction is best-effort and can never
  replace the officer's words with a shortened model summary.
- **Capture now survives service recreation with an on-device checkpoint**, consumes every fix in
  a batched Fused Location update, requests sticky service restart, rejects reversed timestamps,
  and does not wait for reverse geocoding before treating a visit as persistable.
- **Cross-midnight visits appear on both affected days** with each day's clipped duration instead
  of disappearing because their start timestamp belonged to the previous day.
- **Cloud reports fail closed on unknown citations**, retry only typed transient failures, use
  explicit output budgets and bounded responses, and preserve officer-written text around
  unattended daily, midday, and weekly generated blocks.
- **Diary drafts survive process recreation and intentional clearing.** Pending edits are flushed
  before generation, failed note saves keep the typed text on screen, and PDF/ZIP exports are
  written atomically and surfaced through Android's share sheet.
- **Map rendering moved to a dedicated screen.** Today uses a lightweight route preview, keeping
  normal navigation independent of tile-network and MapLibre idling behavior.
- **Cloud-backup restore validates bounds, record counts, coordinates, dates, providers, and the
  complete snapshot before the local Room transaction can replace records.**
- **The schema-6 upgrade reconciles both schema-5 variants safely.** Existing v3.6 installs retain
  DSR imports while gaining named geocodes, and reliability QA installs retain their diary and
  geocode cache while gaining DSR tables.
- **CI now separates build/unit/lint from Android 14 instrumentation**, bounds emulator execution,
  captures failure evidence, and gates tagged releases on both successful checks.
- **A stay's start time was subject to a data race.** The dwell start was read inside the coroutine
  that recorded the stay, while the field was reset synchronously right after that coroutine was
  launched, with no synchronisation between them. Losing the race stores the stay at the epoch,
  where it matches no date and appears nowhere in the app. Tests reproduce the loss reliably; real
  captures on an Android 17 device happened to win it, so the corruption is intermittent rather
  than certain. The start time is now read before dispatching.
- **Stays were lost on a vehicle departure.** A stay ended at the last sample taken inside its
  radius, and the 75 m update filter produces none when the officer drives off, so the whole stay
  was discarded. Stays now end when the departure is detected.
- **Journeys between two places were dropped** when the stay anchor reset mid-trip.
- **The weekly rollup overwrote the same day's diary**, and the unattended 8 PM report overwrote
  anything the officer had typed by hand. Both now keep the officer's text and replace only their
  own block on regeneration.
- **Passive capture stayed dead after a force-stop or an app update** until the next reboot, while
  Today still reported "GPS tracking on". Opening the app re-arms capture, and the status now
  reflects whether the service is genuinely running.
- **Capture was switched off entirely for anyone who granted location "while using the app"**, even
  though a foreground service with the location type keeps receiving fixes.
- **The evening and midday reports ran a cloud round trip inside a broadcast receiver**, which the
  platform kills long before a 180-second request can finish. Both now run in WorkManager, which
  also waits for connectivity; the retry worker previously had no network constraint and burned out
  its attempts while offline.
- **Clearing a diary did not stick** — an empty body was discarded and the old text reappeared.
- **PDF export, the week ZIP export, and audit-log reads and writes ran on the UI thread.**
- Voice capture could hang forever if the recognizer never called back; it now times out.
- Settings no longer discards a half-typed place or a connection-test result when it refreshes.
- File output falls back to internal storage when external app storage is unavailable.

### Security and robustness audit
- **Fixed a crash found by adversarial UI fuzzing.** An Android Monkey run aborted at event 4720
  with `IllegalStateException: LayoutCoordinate operations are only valid when isAttached is true`,
  raised by Compose's directional focus search inside a LazyColumn. It is reachable from a
  hardware keyboard, a D-pad, or accessibility navigation. Fixed by moving to Compose BOM
  2024.09.02 (compileSdk 35, AGP 8.6.1); 14,000 Monkey events across four seeds now run clean.
- Android Lint: 0 errors.
- All 122 shipped dependencies checked against the OSV vulnerability database: 0 vulnerable.
- Release build verified end to end through R8 minification.

### Tests
- Unit coverage expanded substantially, including adversarial suites that fuzz the capture engine with random,
  reversed, repeated, and antimeridian-crossing GPS fixes, and drive the cloud client through rate
  limits, outages, dropped connections, malformed JSON, and oversized answers. The cloud client
  previously had no tests at all.
  New coverage for the capture engine (which had none), the real shipped database upgrade paths
  (2→5, previously untested), geocoding and name extraction, report save semantics, and the feed model.
- Instrumented: added feed and capture-lifecycle tests; permission granting in tests no longer
  races the tests that depend on it.

## 3.6.0 — 2026-09-07

### Added
- **Local-first DSR Command dashboard.** Operational PDF imports are parsed on the device into
  command cards, forecasts, station snapshots, headline metrics, and review warnings.
- Corrected reports replace the active snapshot for the same date instead of double-counting it;
  imported source PDFs remain in app-private storage and are excluded from cloud backup.

### Release
- Re-established the permanent signed-APK publisher and added the PDFBox optional JPEG-2000
  shrinker rule required by the release build.

## 3.4.0 — 2026-08-30

- Replaced the decorative journey sketch with an interactive OpenStreetMap-based map using MapLibre and OpenFreeMap.
- Added chronological route lines, visit markers, automatic journey bounds, attribution controls, an OpenStreetMap full-map action, and a retryable network-failure state.
- Kept one universal APK compatible with arm64, armv7, x86, and x86_64 devices.
- Made synthetic QA seeding idempotent and removed its controls from production builds.
- Hardened encrypted API-key storage, disabled sensitive-data backup, and removed destructive database migration fallback.
- Changed microphone and call-log permissions to request only when their features are used.
- Removed the overlapping voice FAB and dead local-LLM/voice scaffolding; voice capture now reports failures honestly.
- Fixed dateline route rendering, viewport-aware map fitting, map lifecycle catch-up, officer/API-key field visibility, Today metric sizing, diary grammar, place-coordinate validation, provider-chip layout, stale copy, and Nominatim app identification.
- Expanded unit and Compose instrumentation coverage; verified 10 screen-flow tests, repeated journey flows, lifecycle recovery, and adversarial random input on an Android 14 emulator.

## 3.3.0 — 2026-08-30

- Added DeepSeek as the default OpenAI-compatible cloud provider.
- Made diary generation cloud-only with an explicit configuration/network error.
- Removed the native on-device LLM dependency and moved CI instrumentation to the supported x86_64 emulator.
- Added the Smart Field Note launcher icon with Android themed-icon support.
- Hardened microphone and location capture against permission-revocation races.
- Corrected cloud-only product copy and diary labels across the app and PDF export.
- Stabilized Compose instrumentation selectors and release asset generation.

## v3.2.0 — 2026-08-29

State-of-the-art passive upgrades from v3.2 roadmap.

### Features
- **Voice FAB** on Today — SpeechRecognizer → structured voice events
- **Journey map preview** — offline canvas timeline from GPS visits
- **LLM citations** — [V#]/[E#] refs in context; reports cite sources
- **Weekly AI rollup** on History tab
- **Export week package** — ZIP with diaries, PDFs, audit log
- **Frequent-place learning** — suggests geofences from visit clusters
- **Supervisor name** on PDF sign-off line

## v3.1.0 — 2026-08-28

Triple audit loop: adversarial tests, synthetic data, passive LLM features.

### Features
- Midday cloud pulse (1 PM optional) appended to diary
- Significant moment marker (passive flag, no typing)
- Synthetic demo day generator for QA
- Local capture audit log (transparency)
- LLM report retry worker on network failure
- Context limiter for very long days

### Fixed
- Flush open GPS dwell when location service stops
- PDF export with blank officer/text
- Event input truncation (8k chars)
- OSM geocode invalid coordinate guard
- Diary merge without unsafe null assert

### Testing
- Adversarial unit tests (ContextLimiter, EventRepository)
- Maestro E2E flow (`maestro/onboarding_and_nav.yaml`)
- Honest ratings: `docs/AUDIT_RATING.md`

## v3.0.1 — 2026-08-28

E2E hardening: bug fixes, instrumentation tests, and CI emulator suite.

### Fixed
- BootReceiver restarts passive GPS/call capture after device reboot
- Background location requested after foreground grant (Android 11+ flow)
- GPS status chip reflects permissions, not just the settings toggle
- Optional note section collapses after save
- Settings place validation shows errors instead of silent no-op
- SecureApiKeyStore falls back when encrypted prefs unavailable (tests)
- CallLogWorker and LocationService tolerate restricted/test contexts
- Onboarding copy updated for passive GPS + cloud AI model

### Tests
- Compose instrumentation: onboarding flow, bottom-nav, optional note, settings/diary sections
- Robolectric: BootReceiver smoke test
- CI runs `connectedDebugAndroidTest` on API 34 emulator

## v3.0.0 — 2026-08-28

Cloud AI brain + passive daily capture. GPS journey tracking, OpenStreetMap place names, and end-of-day LLM reports.

### Cloud AI
- OpenAI, Anthropic, and OpenAI-compatible providers (Groq, etc.)
- API key stored with EncryptedSharedPreferences
- Settings: provider chips, model name, base URL, test connection
- Auto-generate report at 8 PM when cloud AI is configured
- Local GGUF model remains fallback when no API key

### Passive capture
- Visit detection: stays (≥8 min / ~150 m) and transit segments
- OpenStreetMap Nominatim reverse geocode with on-device cache (1 req/s)
- Foreground location service with passive tracking notification
- Optional end-of-day typed note (not required for reports)

### UI
- Today tab: journey timeline, GPS/cloud status chips, Generate AI report
- Diary tab: cloud-aware generate button and hints
- Settings: Cloud AI section above capture toggles

### Data
- Room v4: `location_visits`, `geocode_cache` tables
- Visit events logged as `visit` type in event timeline

### Privacy note
Generating cloud reports sends your day's activity summary (places, times, calls, notes) to the LLM provider you choose. Keys and raw GPS stay on device until you generate.

## v2.1.0 — 2026-08-28

Mobbin-inspired UI redesign.

### UI
- Warm canvas background, rounded cards (20dp), metric pills
- Timeline event cards with type color bars and chips
- Extended gold FAB for voice on Today tab
- Grouped settings sections, improved onboarding
- Outlined/filled bottom navigation icons

### MCP
- `.cursor/mcp.json` adds Mobbin server URL alongside Figma

## v2.0.0 — 2026-08-28

Production release. Full offline diary workflow with polished UI, navigation, history, tests, and geofence-aware GPS.

### App experience
- Material 3 theme (navy/gold institutional palette)
- Four-tab navigation: Today | Diary | History | Settings
- First-run onboarding (name + privacy)
- Today dashboard with stats, event delete, voice FAB
- Diary tab: generate from logged events or pasted text, edit, multi-page PDF share
- History: browse and open past diaries by date
- Settings: delete named places, improved layout

### Reliability
- Android SpeechRecognizer fallback when Whisper model not bundled
- GPS breadcrumbs matched to named geofence places
- Multi-page PDF export (no truncation on long dairies)
- Room v3 with indexes + migration from v2
- Removed unused VoiceCaptureService

### Tests
- Unit tests: GeofenceMatcher, DairyFormatter, DayBounds, EventExtractor, PdfExporter, Room repositories
- CI runs `testReleaseUnitTest` on every main push

### Docs
- `docs/DESIGN.md` — UI and navigation spec (Figma unavailable)

## v1.0.1 — 2026-08-28

Reliability and release fixes.

### Fixed
- GitHub Actions release workflow: Android SDK setup (v1.0.0 tag build was stuck)
- LLM load when model was imported from Downloads (not bundled in assets)
- Daily 8 PM reminder rescheduled after device reboot

### Added
- CI workflow: release APK build + Python tests on `main`

## v1.0.0 — 2026-08-28

First reliable walking-skeleton release. Fully offline. No cloud, no telemetry.

See [docs/RELEASE.md](docs/RELEASE.md) for install steps.
