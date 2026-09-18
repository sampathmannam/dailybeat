# DailyBeat: accuracy, privacy, and daily usefulness

Date: 2026-09-15. Implementation began 2026-09-16; see [the public-beta status](PUBLIC_RELEASE_PLAN.md).
This document is the complete roadmap, not a claim that all milestones have shipped.

## Outcome

DailyBeat should capture the important parts of an officer's day, distinguish observations from
assumptions, make corrections quick, and produce a diary the officer can confidently review.
Its growing history should answer useful questions and retain confirmed follow-ups. Battery use
and privacy are part of that outcome, not separate polish tasks.

Keep the current Soft Sun accent, Carbon dark mode, phone-only navigation, Android typography,
and DSR separation. Focus design work on task clarity, truthful states, and fewer decisions.

## Verified starting point

- Inspected repository HEAD: `a839e8f`, branch `feature/adaptive-battery-capture`.
- Last verified phone installation: production `com.dailybeat.app`, v4.0.6, version code 27.
- Adaptive capture is implemented on the branch; its sustained physical-device performance is
  unproven. Do not describe it as installed or field-validated.
- Room schema is 8; backup snapshot schema is 2. Inspect HEAD again before choosing new versions.
- Existing strengths: capture, maps, visits, voice/text notes, rename/hide/restore, daily review,
  diary editing, exports, private zones, backup, local insights, and release safeguards.
- No app code or phone settings are changed by this plan. The existing untracked `outputs/`
  directory is user-owned and outside this work.

### Findings that determine the order

| Finding | Evidence in current code | Consequence |
| --- | --- | --- |
| Citation checking is structural | `cloud/ReportIntegrityValidator.kt` accepts known IDs and requires only one valid citation when sources exist | An unsupported sentence can still pass; valid IDs do not prove factual support |
| Automatic visits default to confirmed | `data/model/LocationVisit.kt`: `reviewState = "confirmed"` | The old flag cannot be treated as evidence of human confirmation |
| Review changes underlying visits | `ui/review/ReviewDayViewModel.kt` updates name/hidden fields directly | Introduce correction history before adding more powerful repairs |
| Day completion is a simple flag | `data/repo/BeatRepository.kt` | Completion is not tied to the source revision or diary version the user reviewed |
| Gaps use elapsed time between points | `ui/feed/DayFeedItem.kt` | Intentional stationary capture silence can look like failure; missing beginning/end intervals may be invisible |
| Privacy filtering is visit-specific | `domain/OutboundVisitFilter.kt`, `cloud/DayContextBuilder.kt` | Notes and other text need their own sharing policy; coordinate removal alone is insufficient |
| Exports copy stored prose | `export/PackageExporter.kt`, `export/PdfExporter.kt` | Later privacy changes cannot be assumed to remove older mentions from a saved diary |
| Backup sends a readable snapshot | `backup/BackupCoordinator.kt`, `SupabaseBackupClient.kt` | Transport/authentication safeguards are not end-to-end backup encryption |
| Insights mainly summarize activity and review | `ui/insights/InsightsViewModel.kt` | Search and confirmed follow-ups would add more practical value than more charts |

Paths in this table are relative to `android/app/src/main/java/com/dailybeat/app/`.
Earlier release documents describe intended privacy guarantees more broadly than these inspected
paths establish. Treat those guarantees as requirements to verify, not proof that all paths comply.

## Product decisions

1. GPS supports an observed location and approximate time. It does not establish the activity,
   people present, decisions, attendance, or outcomes at that location.
2. Preserve three separate ideas: evidence origin, user confirmation, and sharing permission.
   A confirmed item can still be private. A recorded observation can still be approximate.
3. Keep raw evidence and accepted corrections distinct. Manual insertions never manufacture GPS
   points or fill map gaps with invented routes.
4. Generated text starts as a draft. User confirmation is an attestation, not independent proof
   that an event occurred. Never describe a PDF as officially submitted merely because it exists.
5. Keep source capture, review, text notes, local search, and saved diary access usable offline.
   Existing cloud AI remains optional to these paths; no new offline LLM is required.
6. Prefer local, deterministic processing. Do not add a remote vector store, continuous AI calls,
   social features, movement scores, automatic third-party messages, or a dependency bundle.
7. Keep all measurement data local by default. Diagnostic sharing is an explicit user action.

## Architecture to establish once

Use the existing Room/repository/ViewModel/Compose stack. Add focused domain services rather than
rewriting the app or adopting a new framework. Proposed type names below are implementation names,
not new concepts to expose in the normal UI.

| Component | Responsibility | Consumers |
| --- | --- | --- |
| Evidence identity and revisions | Stable source IDs, origin, capture time/zone, revision, and confirmation provenance | Review, reports, notes, search, backup |
| `VisitCorrection` and `EffectiveDayRepository` | Original evidence plus reversible rename/time/merge/split/hide/manual corrections | Today, Days, map, review, reports, insights |
| `CaptureSession`/`CaptureStateInterval` | State intervals and reasons, expected collection behaviour, callback freshness | Capture health, gaps, diagnostics, field evaluation |
| `OutboundDayBuilder` | Destination-specific inclusion, privacy revision, source manifest, preview payload | Daily/weekly/custom reports, PDF, text and ZIP sharing |
| `DiaryRevision`/`DiaryStatementSource` | Draft text, source snapshot, statement links, accepted version, review state | Diary editor, source drawer, export |
| `EventVisitLink`/`FollowUp` | User-confirmed note associations and explicit actionable commitments | Add Moment, review, history, insights |
| `BackupEnvelope` | Authenticated ciphertext, format/key version, recovery metadata | Backup and restore |
| Local search index | Derived searchable content with current visibility rules | Days search and later constrained question answering |

Use stable source identifiers across backup and restore; `[V1]` remains a presentation alias scoped
to one report's source manifest. Never resolve an old citation against a newly ordered visit list.
Separate the accepted content revision from later privacy policy changes: sharing always applies
the current policy even when the user opens an older accepted diary.

Corrections should use transactions with a source revision precondition. If capture updates a visit
while review is open, reload or reconcile explicitly rather than overwriting newer data. A merge
retains its original source IDs; a split retains parentage; a manual stop has no synthetic coordinate.
Store links explicitly, not by matching free-text place names. Day-clipped UI values must never
overwrite the original start/end of a cross-midnight visit.

## Milestones and implementation work

Implement each item as a complete storage → domain → UI → verification slice. Stages are dependency
gates, not promises that an entire stage fits in one calendar day or one production release.

### M0 — evidence baseline and immediate trust boundaries

**Deliverable:** a reproducible baseline and a small QA candidate that clearly labels generated
content as draft and exposes an outgoing-content preview before sharing.

- **T01: Baseline.** Record branch/commit, package, schema, existing gate results, and outstanding
  device-test limitations. Create synthetic fixtures for station visits, duplicate stops, a missed
  stop, an overnight visit, a private note, an edited diary, and a long day requiring context limits.
  Do not copy personal routes into the repository or reset production BatteryStats.
- **T02: Trust controls.** Audit every generation/export entry point, including custom-event
  generation, weekly reports, scheduled reports, retries, PDF, text, and ZIP. Label generated
  material as draft and remove the misleading `Submitted via DailyBeat` PDF footer. Show the exact
  outgoing content, destination, and draft/review status. Keep diagnostics out of diary ZIPs by
  default; create a separate deliberate diagnostic export.
- **T03: Sharing policy.** Add a privacy revision and an initial common outgoing-content policy.
  Privacy changes invalidate queued payloads and previews. Pause automatic cloud generation until
  its inclusion policy has been explicitly configured; existing preferences must not silently opt
  users into a materially broader data transfer. Never overwrite an accepted diary unattended.

**Exit check:** an old diary containing a newly private stop cannot silently pass through an old
preview or queued export. Unstructured legacy prose is identified as requiring content review;
do not promise that text matching automatically removes every sensitive reference.

### M1 — evidence, corrections, and trustworthy review

**Deliverable:** one consistent, reversible daily record across all screens and outputs.

- **T04: Additive evidence model.** Introduce source identities, correction history, content/privacy
  revisions, and confirmation provenance. Preserve legacy text and IDs. Backfill legacy
  `confirmed` as legacy/unknown confirmation provenance, not human-confirmed. Previously completed
  days remain readable with their historical status and an explanation of its limited provenance.
- **T05: Complete repair tools.** Add rename, arrival/departure adjustment, merge duplicate stops,
  split a stay, insert a missed stop, hide/restore, and undo. Validate time order and overlaps,
  handle overnight/time-zone cases, preserve original coordinates, and make conflicting changes
  recoverable. Introduce an explicit confirm action; hiding an item is not factual confirmation.
- **T06: Focused review.** Open on uncertain items and relevant exceptions; allow access to the
  whole timeline. Show recorded/estimated, user-added, and confirmed states in plain language.
  Uncertain gaps can remain acknowledged as unknown; never require invented data to complete a day.
  Bind completion to the exact revision reviewed. New facts or corrections mark the current
  version as changed while preserving the previous accepted version.

**Owners:** `data/model/`, `data/db/`, `data/repo/VisitRepository.kt`, `BeatRepository.kt`,
`ui/review/`, `ui/feed/DayFeedItem.kt`, map consumers, snapshot codec and restore.

**Exit checks:** duplicate-stop merge and undo; cross-midnight time repair without truncating the
source; edit during an incoming location update; process death mid-edit; corrected values match
Today/Days/map/report/search; no raw history loss; accepted revision survives later changes.

### M2 — source-linked diary generation and safe sharing

**Deliverable:** a diary whose claims can be inspected and whose shared content follows current
privacy choices.

- **T07: Structured statement contract.** Generate bounded structured statements with explicit
  source IDs and claim types. A location-only source supports only a cautious visit/time statement.
  Activities, people, and outcomes require user-provided or user-confirmed event evidence. Use a
  deterministic template for location statements; keep unsupported AI interpretations as proposals.
  Validate IDs, required fields, time bounds, source eligibility, and per-statement source presence.
  An additional LLM check may flag problems but never serves as proof of factual correctness.
- **T08: Reviewable diary revisions.** Save the manifest and generated draft separately from
  user text. Tapping a statement opens its sources and confirmation status. Editing a generated
  statement marks the new text as user-edited and its old source links as needing review; never
  imply unchanged automatic support. Regeneration creates a candidate revision and preserves the
  accepted version. Retain explicit acknowledgements for unresolved uncertainty.
- **T09: One outgoing path.** Route manual, scheduled, retry, weekly, and custom-event generation
  through the same destination policy. Exclude coordinates from AI prompts; apply privacy to
  events, note associations, identities, and saved/generated text as well as visits. Hide/private
  changes invalidate affected source-derived claims. Require manual review for unlinked prose.
  Make the preview payload the payload sent; revalidate content/privacy revisions before sending.
- **T10: Honest export.** Offer reviewed output and clearly marked draft output. Neither claims
  formal submission. Show a redacted preview with omissions explained. Do not reuse cached PDFs
  or ZIPs after content/privacy changes; ensure the actual shared file matches the preview.
  Explain that content already delivered to another app/provider cannot be recalled by a later edit.

**Owners:** `cloud/DayContextBuilder.kt`, `ReportIntegrityValidator.kt`, `ValidatedReportClient.kt`,
`ReportGenerator.kt`, `WeeklyReportGenerator.kt`, `PulseReportGenerator.kt`, retry workers,
`ui/diary/`, `export/`, `domain/OutboundVisitFilter.kt` and replacement/common policy.

**Exit checks:** a GPS-only visit cannot become an inspection claim; an unknown or truncated source
cannot be cited; every substantive generated statement has eligible sources; private facts cannot
reappear through notes, retries, custom generation or weekly ZIPs; accepted edits are preserved.
Use a human-labelled synthetic/adversarial corpus. Zero unsupported activity claims in that corpus
is a release gate, not a claim that unrestricted language generation can never make an error.

### M3 — encrypted backup with tested recovery

**Deliverable:** backup contents encrypted on the phone, with a replacement-phone restore that
does not depend on the old phone's Keystore key.

- **T11: Encryption design.** Use platform authenticated encryption, proposed AES-256-GCM, with a
  fresh nonce for each encryption and authenticated envelope metadata. Bind format version,
  backup/account identity and key ID; reject altered metadata, ciphertext, truncation, and
  unsupported versions. Keep server-side authentication and owner-only access controls.
- **T12: Recovery.** Generate a random backup encryption key; protect its local copy with Android
  Keystore. Provide a high-entropy recovery secret that wraps/recovers that key independently of
  this device. Verify recovery setup through a simple confirmation exercise. Never store the
  recovery secret, plaintext data key, or provider API key with the server backup. Password reset
  alone cannot decrypt the backup. Handle device-key invalidation without deleting the only copy.
- **T13: Migration and rollout.** Introduce a versioned ciphertext envelope alongside existing
  readable snapshot compatibility. Test restore into isolated storage before any production
  replacement. Upload, download, decrypt and validate the encrypted backup before replacing the
  old active backup. Give legacy-backup retention/deletion an explicit migration choice; do not
  claim historical provider copies have been erased. Preserve full private-zone data in the
  encrypted personal backup, distinct from redacted report sharing.
- **T14: Old-client protection.** Define server capabilities and per-account encryption state.
  Once an account migrates, reject legacy plaintext writes so an older installed client cannot
  silently downgrade protection. Keep legacy reads only where deliberately supported. Cover schema
  compatibility, key rotation/recovery-key replacement, and a cancelled/interrupted restore.

**Owners:** `backup/`, Room migrations, `supabase/migrations/`, RLS tests, live-backup QA,
`ui/settings/`, release backup-format compatibility checks.

**Exit checks:** fresh-device recovery using the recovery secret; wrong secret; unavailable old
device; corrupted envelope; expired session; interruption; unsupported version; old-client
downgrade; account isolation; restored corrections, privacy flags and diary sources remain intact.
Restore validates and stages everything before one transactional replacement. Test on QA data.

Scope: this protects cloud backup contents, not data deliberately sent to an AI provider or a
shared PDF. It is not a promise that an unlocked or compromised phone cannot expose local data.
Review the envelope/key lifecycle as a focused security task before calling the feature complete.

### M4 — reliable adaptive capture and honest battery evidence

**Deliverable:** the existing adaptive implementation verified against coverage, with trustworthy
capture states and a reproducible energy comparison.

- **T15: State-aware coverage.** Persist coordinate-free state transitions and capture sessions,
  not just the latest motion state. Distinguish moving, settling, stationary watch, privacy pause,
  permission blocked, stale/unavailable, and unknown after restart. Derive gaps from expected
  collection and session bounds; include missing start/end intervals. A stationary watcher is not
  proof of an exact location or continuous sensor coverage. Do not infer health merely from a
  persisted `watcherArmed` boolean; verify registration/rearming and expose uncertainty.
- **T16: Finish adaptive reliability.** Verify motion transitions, denied activity permission,
  foreground-service eligibility, reboot, process death, Doze, low-power mode, long indoor stops,
  walking/driving transitions, late/out-of-order batches, and clock changes. Validate that stopping
  the service flushes buffered fixes and closes/preserves the current visit correctly. Keep a
  tested baseline capture fallback when the watcher is unavailable. Never bridge unknown routes.
- **T17: Lightweight diagnostics.** Record session state durations, delivered/accepted/rejected
  fix counts, age, restart/failure reason, and charging/battery snapshots at existing lifecycle or
  state events. Bound retention and batch writes. No frequent battery polling, extra wake locks,
  content logging, or cloud telemetry. Runtime app diagnostics must not invent a per-app energy
  percentage; Android attribution is collected externally for evaluation.
- **T18: Energy protocol.** Separate whole-device discharge, Android UID-attributed estimated
  consumption, charging intervals, foreground UI time, and shared-system attribution limits.
  Compare equal-length unplugged sessions on the same handset with screen/network/movement context.
  Use the old and new capture policies on separate matched runs; never run two full trackers
  concurrently for the comparison. Preserve raw snapshots locally without resetting system stats.

**Owners:** `capture/`, `CaptureHealthStore.kt`, `ui/feed/DayFeedItem.kt`, Today/Review coverage,
local diagnostics and explicitly serial-targeted QA scripts.

**Exit checks:** no silent capture disablement; expected stationary silence is not a fabricated
gap; true movement gaps remain visible; missed-stop rate is no worse than baseline. Field results
must precede claims about daily drain. The earlier 3 h 20 m battery sample is contextual evidence,
not a 24-hour benchmark. Android recommends batching and motion-aware location patterns; actual
benefit on this Motorola remains an empirical question. [Android location guidance](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios)

M4 can start after M0 and the shared session/evidence contracts are defined in M1. It does not have
to wait for search, but its completion/coverage semantics must match the review model before release.

### M5 — quick moments, confirmed follow-ups, and useful history

**Deliverable:** capture meaning with little effort and retrieve it later without cloud dependence.

- **T19: Quick Add Moment.** Add a pinned app shortcut first, then a minimal home-screen widget
  using the same entry point. Open the capture sheet; require an explicit tap before microphone
  recording. Save typed text and recognition results locally before optional AI processing. Preserve
  editable drafts through process recreation, errors, denied permission, and cancelled recognition.
  Do not promise offline speech recognition when the configured Android service lacks it.
- **T20: Associate and act.** Suggest the nearest relevant visit with a visible editable link;
  association remains provisional until confirmed. Extract possible follow-ups into proposals.
  A reminder is scheduled only after the user confirms the task and date; ambiguous dates stay
  unresolved. Link tasks back to their note/day, support edit/done/snooze, and prevent duplicate
  reminders after retries or restore. Lock-screen notifications reveal no sensitive note content.
- **T21: Local search.** Add search inside Days by place, note/diary text, date range, and confirmed
  follow-up status. Use Room/SQLite full-text search where supported by the pinned Room version,
  parameterized queries, and a rebuildable derived index. Search updates with corrections,
  hiding, deletion, and restore. Hidden records are omitted by default; a deliberate local-only
  filter can reveal them. Local search visibility and outbound sharing policy remain separate.
- **T22: Better Insights.** Prefer pending confirmed follow-ups, last confirmed visit, and recent
  relevant notes. Show sources and denominators, including incomplete capture. Keep distance and
  streaks secondary and do not derive productivity from movement. Later add constrained local
  questions such as “When did I last visit X?” using these same queries and source links. Open-ended
  conversational memory is deferred until retrieval is demonstrably useful and accurate.

**Owners:** `ui/today/`, `capture/VoiceCaptureOrchestrator.kt`, `capture/SpeechTranscriber.kt`,
event repositories, Android shortcut/widget entry points, new follow-up DAO/worker,
`ui/feed/`, `ui/insights/`, backup snapshot/recovery integration.

**Exit checks:** typed note saved offline; failed cloud structuring loses nothing; shortcut and
normal Add Moment behave identically; wrong suggested visit is correctable; no reminders without
confirmation; no duplicates after restart/restore; local search returns linked current records
and works without network. Add realistic English and locally used mixed-language search fixtures.

## User experience across stages

- Today: one compact truthful capture status, route evidence, Review my day, and quick Add Moment.
  Keep recovery near the affected status; detailed diagnostics remain in Settings.
- Review: exceptions first, whole-day context available, undo, source inspection, and an explicit
  accepted revision. “Unknown” is a legitimate outcome for a missing interval.
- Diary: readable draft, tap-to-source, user edits protected, version selection, final outgoing preview.
- Days: existing records plus local search and useful date/place filters, not a new top-level tab.
- Insights: confirmed pending actions and useful recall; no extra dashboard card wall.
- Settings: privacy/backup recovery/capture controls with understandable consequences.

Preserve the chosen palette. Validate light/dark, phone portrait/landscape, 200% text, TalkBack,
48 dp targets, keyboard insets, Back, and reduced motion on affected screens. Prefer existing
Material components. No Penpot/Figma integration is a blocker to implementing these focused flows;
update design artifacts later if they materially aid review.

## Success scorecard and 14-day field trial

These are proposed acceptance targets, not results already achieved. A small personal pilot is
evidence for this user/device, not proof of performance across all Android phones.

| Outcome | Proposed target | Measurement |
| --- | --- | --- |
| Correct meaningful stops | At least 95% recall for independently noted stops lasting at least 10 minutes; report false positives separately | Brief manual reference list; match by place and overlapping time; report numerator/denominator and short stops separately |
| Fast evening review | Median at most 60 seconds on at least 10 evaluable days; at least 80% under 90 seconds | Start at Review, stop at acceptance; report interruption exclusions and complex-day times |
| Trustworthy generated activities | Zero unsupported activity/person/outcome claims in the labelled release corpus; none in accepted pilot diaries | Compare statements against source evidence; track corrections and disagreements explicitly |
| Honest gaps | Every induced moving-state outage appears; intentional pause/stationary-watch intervals are labelled correctly | Controlled QA scenarios plus manually checked field exceptions |
| Battery | Aim for under 2 percentage points of attributable incremental drain per 24-hour equivalent; coverage must not regress | Matched unplugged sessions, state/use context and attribution caveats; do not sum or directly equate UID estimates with whole-phone discharge |
| Quick notes | Typed note stored locally immediately after save; cloud/recognizer failure does not erase existing draft | Fault injection and device lifecycle checks |
| Useful search | At least 95% expected records found in the labelled query set; p95 response below 300 ms for 10,000 indexed records on the reference phone | Local benchmark and realistic queries; no network |
| Recovery | All required fields survive valid restore; wrong/corrupt input changes no local records | Snapshot equality and staged transactional restore checks |
| Privacy | No prohibited fixture data in outgoing payloads/files under the configured policy; backup server payload has no plaintext diary/location content | Fake network capture, file inspection, mutation/retry cases, encrypted round-trip tests |

Battery target reporting: a daily-equivalent normalized figure is only eligible after multiple
representative sessions spanning stationary/moving/screen-on/off conditions. Report range and
confounders, retain actual durations, and do not describe a noisy whole-phone difference as precise
app attribution. If available evidence cannot resolve a 2-point target, mark it inconclusive and
extend the comparison. Do not reduce capture quality merely to satisfy the target.

Trial sequence, after the relevant QA gates pass:

1. Days 1–3: baseline behaviour and normal work patterns; keep a small independent stop/reference
   list. Reuse recent equivalent baseline days only when their measurement scope is documented.
2. Days 4–6: matched candidate capture runs and correction review; investigate any missed movement
   before continuing. Do not use concurrent trackers on the same phone.
3. Days 7–14: integrated everyday workflow with the reference list, review timing, battery snapshots,
   search questions, and confirmed follow-ups. Repeat a fresh-device recovery drill on QA storage.
4. Evaluate raw counts/ranges and unresolved issues. If fewer than 10 review days are evaluable,
   extend the review evaluation. Any material capture-policy change restarts the affected evidence.

Prototype verification and field trial are distinct. Destructive tests use only an explicitly
selected disposable QA package/device. User production data is never a test fixture. A production
policy switch or signed install-over-existing update happens only as part of a requested, reviewed
release, after safe migration checks; plan authoring does not initiate it.

## Delivery order and release gates

| Candidate | Included work | Gate before advancing |
| --- | --- | --- |
| A: Trust boundary | M0, shared M1/M2 design and additive migrations | Source/egress inventory complete, truthful draft/export behaviour, fixtures and migrations verified |
| B: Reliable daily record | M1 + M2 + M4 | Corrections/revisions consistent, per-statement source checks, current privacy policy enforced, device capture checks pass |
| C: Recoverable private history | M3 | Ciphertext recovery and legacy/old-client protections pass; backup format and account transition reviewed |
| D: Useful memory | M5 and focused UI verification | Quick notes, confirmed reminders and local search work offline and survive upgrade/restore |
| Field-validated candidate | Integrated candidate and 14-day protocol | Scorecard met or limitations explicitly documented; no open data-loss, privacy or unsupported-claim defects |

Do not assign production version numbers, publish, or merge simply because a candidate exists.
Each candidate should be reviewable as focused commits/PRs when requested; do not ship one enormous
unreviewed rewrite. Code dependencies can be developed independently after shared contracts are
fixed, but release gates stay intact. No agents or automations are started by this plan.

For each applicable candidate:

- Run the existing Python release-policy suite and Android build/unit/lint gates.
- Add behaviour-based tests for migrations, correction transactions, evidence eligibility,
  privacy mutation, recovery, and restart behaviour. Enable/review exported Room schemas as part
  of migration work; validate the full supported upgrade chain, not only a fresh database.
- Update backup codec/validator/restore and all new entity relationships in the same slice as the
  schema change. Rebuild indexes after restore; reconcile scheduled tasks idempotently.
- Compile instrumentation; run it only against the explicitly chosen QA serial and suffix.
  Never invoke a broad connected-device test while an unrelated production phone is attached.
- Use dedicated QA credentials for live encrypted-backup round trips. Maintain RLS, dependency
  verification, permanent signing, CodeQL, offline instrumentation, and release contract checks.
- Keep a per-candidate report: exact commit, package/version/hash, changes, measured checks,
  screenshots where useful, unrun checks, migration formats, known limitations, and next step.

Rollback means disabling a new optional feature or shipping a forward-compatible corrective
release. Do not recommend installing an older binary against a newer schema, deleting the app,
or restoring an old plaintext backup as an automatic recovery action. Preserve compatible reading
and accepted history through feature fallback. Freeze external sharing if its privacy policy fails.

## Deferred work and decisions

Out of scope for this improvement cycle: social features, leaderboards, employee tracking, DSR,
PatrolGrid, general AI chat, remote embeddings, broad SDK upgrades, permanent high-accuracy GPS,
new themes, an offline language model, and aggressive background polling.

Default decisions are already made above: local search first, existing Android stack, preserved
visual theme, exception-focused review, recovery-secret-based backup, and confirmed reminders.
No preliminary preference question is needed to begin the implementation after a build request.
Specific user actions arise later at the right moment: choosing where to keep the recovery secret,
confirming legacy-backup cleanup, consenting to cloud inclusion rules, and reviewing a production
release. Present each with a working QA result and clear consequences.

## Technical references

Implementation should verify the installed library/platform versions before adopting API details.

- [Android location battery optimization](https://developer.android.com/develop/sensors-and-location/location/battery/optimize): batching and request lifecycle.
- [Android motion-aware location scenarios](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios): activity recognition with location updates.
- [Android cryptography](https://developer.android.com/privacy-and-security/cryptography): platform primitives including AES-GCM; use platform implementations rather than custom cryptography.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): device key protection; portable recovery must be designed separately.
- [Room FTS4](https://developer.android.com/reference/androidx/room/Fts4): local full-text indexing; verify compatibility with the pinned Room version.
- Repository: `PRODUCT.md`, `docs/ADAPTIVE_BATTERY_CAPTURE.md`, `docs/RELEASE.md`, and existing security/migration tests.

## First executable implementation slice

Start with T01–T03: establish the baseline, enumerate every outgoing-content path, add accurate
draft/export states and previews, and protect against stale privacy revisions. Then implement
the evidence/correction model before expanding review controls or trusting generated statements.
This makes the subsequent battery, recovery, note, and search work depend on one coherent record.
