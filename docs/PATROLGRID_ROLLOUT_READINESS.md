# PatrolGrid rollout readiness ledger

Updated: 2026-09-02

This is the post-fix working ledger. The historical findings in
`PATROLGRID_UX_AUDIT.md` remain pinned to baseline commit `61342b5` and are not
rewritten as if they had originally passed.

## Current decision

**Controlled synthetic/managed pilot only. Not yet approved for subdivision-wide
staff rollout.** Core patrol, assignment, evidence, review, offline synchronization,
duty-window, and authorization paths are implemented and have substantial repeatable
coverage. Full rollout still requires the external identity/device/privacy controls
and the unfinished product/verification work listed below.

Status legend: **PASS** repeatably verified; **BUILT** implemented but its configured
end-to-end journey remains pending; **CONDITIONAL** needs a staging service or device
state; **OPEN** not yet complete.

## Screen and control inventory

| Surface | Controls and states | Status | Repeatable evidence |
|---|---|---|---|
| Privacy gate | All six disclosure sections, policy link, versioned acknowledgement, synthetic-QA warning, 200% text scrolling | PASS | Dedicated Compose and persistence tests passed in the final combined and both-device runs |
| Managed sign-in | Email, password, show/hide, blank-disabled submit, loading-disabled state, safe error, locked-app copy | PASS | Dedicated Compose states pass on both devices; hosted Auth failure/session-expiry journey remains CONDITIONAL |
| Debug onboarding | Welcome Continue; officer name; both role choices; disabled/enabled Continue; privacy Get started | PASS | Instrumentation plus emulator and Motorola Maestro |
| Patrol navigation | My Patrol, Missions, More; absent unfinished Messages | PASS | Connected navigation and Maestro on emulator/Motorola |
| Supervisor navigation | Control, Missions, Units, More; absent unfinished Alerts | PASS | Every compact destination passes on both devices; wide rail destinations were rendered and activated manually |
| My Patrol briefing | Assignment, duty/status, priorities, route context, privacy boundary copy | PASS | Connected and Maestro |
| Patrol start | Permission guard, mission scope, expired-window refusal, encrypted tracking start | PASS | Connected tests, repository tests, Maestro, deadline regression |
| Priority visit | Current-only enabled row and visited-state transition | PASS | Connected action passes on emulator and Motorola |
| Field updates | Observation, Deviation, Safety event; required detail; 4,000-char bound; Cancel and Save | PASS | All three inputs, validation, Cancel and Save pass; RPC/outbox/E2E coverage also passes |
| End patrol | Cancel; Completed, Relieved, Device issue, Mission cancelled; automatic duty-window end | PASS | Every reason, confirmation/cancel and automatic deadline path pass |
| Missions | Every mission row opens detail; alternate assigned mission selection; Back | PASS | Connected instrumentation and both-device Maestro |
| Mission evidence | Planned/recorded route, one-person/one-session source selection, provenance, priorities, bounded count, geographic map plus tile-free fallback | BUILT | Exact-session client/RLS/E2E tests and the dedicated source-selector Compose test pass; configured staging screen journey remains CONDITIONAL; pan/zoom gesture remains OPEN |
| Review context | Exact review linkage, field response, offline outbox | BUILT | pgTAP/E2E/client tests PASS; configured field-response button journey remains CONDITIONAL |
| Supervisor control | Active, Needs review, Upcoming; attention/active/upcoming drill-down; both Assign entry points | PASS | Tabs, attention/active/review/upcoming cards and both Assign entries pass on both devices |
| Assignment sheet | All three routes, all three units, both guidance modes, validation, Assign, Back dismissal, empty Close/Refresh | PASS | Full native interaction suite passes on emulator and Motorola; cross-device Maestro covers entry/Back due a Maestro sheet-gesture limitation |
| Review dialog | Approved, Needs context, Technically inconclusive; required notes; Cancel and Submit | PASS | All outcomes and controls pass; atomic/version-conflict backend paths also pass |
| Units | Scrollable staffed-unit roster and empty state | PASS | Compact and wide destination activation pass; rows remain intentionally read-only until unit detail exists |
| More | Role/access disclosure, sync status, immediate Lock, guarded server Sign out | BUILT | Local role controls PASS; configured lock/sign-out UI remains CONDITIONAL |
| Error/offline | Top Retry, assignment empty Close/Refresh, cached briefing, session expiry | CONDITIONAL | Unit/client/outbox tests cover state; needs controlled configured-staging UI fault injection |
| Adaptive UI | Bottom navigation on compact; rail and bounded content on wide windows; entry/privacy scrolling | PASS | Compact device runs plus a 1,080 dp rendered rail/control activation pass; full tablet/foldable/landscape matrix remains OPEN |

## Verified backend and reliability evidence

- Clean local Supabase reset through migrations `001`–`010`: PASS.
- Full pgTAP database suite: **280/280 PASS** across the **274-test PatrolGrid
  authorization/workflow/retention suite** and the **6-test DailyBeat backup RLS
  suite**. The legacy backup table now has exact CRUD privileges instead of
  inheriting Supabase default `TRUNCATE`, `REFERENCES`, or `TRIGGER` grants.
- Full synthetic Auth/PostgREST lifecycle: **1/1 PASS**, including direct-write
  denial, cross-user/subdivision denial, exact-session route/priority provenance,
  review versioning, duty closure, and bounded delayed evidence.
- Database lint: PASS with no public-schema errors.
- Locust final evidence-ingestion run: **1,053 requests, 0 failures, aggregate p95
  10 ms** at 10 users for 15 seconds. A later run executed concurrently with the
  local emulator completed **796 requests, 0 failures, aggregate p95 88 ms**.
- Current Android gate: **168/168 JVM tests PASS**, Android-test compilation and lint
  PASS, and QA/instrumentation APKs assembled with AGP 8.10.1 / Gradle 8.11.1.
- Latest connected native suite on the current working set: API 34 emulator **41 executable tests PASS** with the
  credential-dependent `CloudBackupLiveTest` explicitly skipped (**42 discovered,
  0 failures**). The most recent Motorola full run remains **37 executable PASS** with
  the same single skip (**38 discovered, 0 failures**); the phone was no longer visible
  over ADB for this final notice-only rerun. This includes six Room migration tests
  covering every stored-schema upgrade from v2 through v7. The current working-set QA
  APK and instrumentation APK were exercised on the API 34 emulator; the Motorola phone
  is presently disconnected.
- Final live emulator patrol smoke: real MapLibre basemap loaded, an encrypted fix
  changed the already-open screen from 2 to 3 route points without refresh, the
  foreground location service remained active only during patrol, and End patrol
  stopped it.
- Final Maestro: emulator **2/2 PASS** (2m23s + 1m05s); Motorola isolated QA package
  **2/2 PASS** (86s + 55s). Full route/unit/guidance submission is covered by the
  native suite on both devices; Maestro uses a no-scroll assignment-entry smoke to
  avoid its Material bottom-sheet gesture conflict.
- QA artifact identity: `com.dailybeat.app.patrolgrid.qa`, target SDK 36. Motorola
  production `com.dailybeat.app` remained byte-for-byte unchanged before and after
  testing.
- Open-source map gate: MapLibre + OpenFreeMap/OSM is now the keyless release default;
  the real basemap and attribution rendered on device, and the release workflow has no
  paid-provider/API-key dependency. The tile-free evidence fallback remains available.
- Direct APK gate: canonical package `com.dailybeat.app.patrolgrid`, version 1.0.0 / code
  10000, exact-key signed annotated tags, current-protected-main equality, monotonic
  versions, and exact package/version/commit/backend/privacy/merged-manifest checks are
  implemented. GitHub has no APK keystore/password, decryption key, or symmetric transfer
  secret. A separate policy job runs hash-locked tests; the artifact job builds only an
  unsigned minified candidate and encrypts the APK, mapping, SBOM, and exact manifest to
  the committed public encryption subkey. Only that ciphertext crosses jobs or enters a
  draft. The no-checkout publisher binds the Actions artifact id/archive digest and the
  separate ciphertext digest. The Mac ceremony independently downloads and byte-compares
  the Actions artifact with the draft before offline signing; APK-signed metadata preserves
  the artifact/draft/workflow/commit and all content hashes. A root-installed audited
  launcher prevents accidental execution from a dirty checkout, performs a fresh signed-
  tag checkout, and invokes fixed-tool signing/installation. It does not protect against
  malware running as the designated signing account; hardware-backed/per-use-presence
  signing or a dedicated offline account/device remains required for high assurance.
  The private ceremony additionally proves the GitHub CLI is the exact repository owner
  with current admin authority before it reads vulnerability alerts, Dependabot security
  updates, secret-scanning, or push-protection state; unavailable or disabled controls
  stop the ceremony.
  The centralized verifier also rejects qualified `xml-vNN` files and `values-vNN`
  aliases for network security, data extraction, and FileProvider paths; all three
  protected resources must resolve to one exact unqualified compiled value.
  Live `main` ruleset `22066728` now has no bypass, squash-only PRs, required signed
  commits, and required `build`, `patrolgrid-backend`, `dependency-review`, and `codeql`;
  tag rulesets `22066729`/`22066730`, selected full-SHA Actions policy, and the exact
  owner-reviewed `patrolgrid-v*` production environment are active. Validate the first
  signed squash PR. The environment now contains no secrets: the obsolete transfer key
  is deleted and the two required production Supabase mobile values are intentionally
  absent. Source remains `UNCONFIGURED`/`UNAPPROVED`, FileVault is off, the audited root
  bundle/two offline backups are not installed/verified, and independent local or
  reproducible source-to-APK comparison is not implemented. No production tag or release
  exists; these conditions intentionally keep release and rollout closed.
- Retention gate: the backend clock is server-owned and starts at the mission's first
  `needs_review`, `completed`, or `cancelled` transition. Terminal missions cannot be
  reopened; later work requires a new assignment, so review or a privileged retry cannot
  reset the clock. Evidence becomes deletion-eligible after exactly **8,760 hours
  (365 days)**. A five-minute scheduler drains a bounded maximum of 2,000 deletable
  missions per invocation, removes dependencies and mission-linked audits in order, and
  records aggregate backlog count/age. Physical deletion is subject to the documented
  15-minute operational SLO, not claimed to occur in the boundary millisecond. Active
  documented holds and anomalous open sessions fail closed; expired assigned missions
  with no session auto-cancel after a five-minute grace so they acquire a clock. Hold
  placement/review dates must be finite, continued holds are reviewed at least every 30
  days, and a placement reference remains immutable after review/release. Administration
  is service-role-only, scheduler workers are database-owner-only, and the purge ledger
  contains aggregate counts rather than mission, staff, route, or note data.
- Device-retention gate: valid encrypted route points and queued field actions become
  due from the mission's single server-issued `retention_until`; point/action timestamps
  never drive deletion. Cleanup completes behind a protected-content/startup and GPS gate,
  runs every 24 hours without a connectivity requirement, and runs before sync. A missing
  server clock has a bounded close-recovery window, then fails closed while authenticated
  recovery remains possible; generic HTTP 400/404 failures never authorize local deletion,
  while explicit server `P0002` removal dead-letters the mission once. Destructive
  cross-store work is preceded by an aggregate intent journal, Room count/delete is
  transactional, malformed/expired encrypted snapshots are swept, and process recreation
  completes an interrupted aggregate record. Expected expiry shows an acknowledgeable,
  persistent aggregate-only integrity incident; genuine enforcement failure blocks capture
  until cleanup succeeds. The encrypted snapshot format rejects legacy mission-merged
  trails after upgrade, and a recently refreshed snapshot is discarded with its mission
  exactly when the authoritative deadline becomes due rather than receiving another
  24-hour cache window. Android may defer work while powered off, so the next successful
  startup check removes overdue evidence before showing it.

## Implemented since the baseline audit

- Explicit mission selection and complete mission detail for both roles.
- Geographic planned-vs-recorded route with priority coordinates and a privacy-safe
  tile-free fallback, whole-attempt load timeout, Retry, stable first camera fit, and
  explicit overlay/integrity failure states.
- Live Room-backed route rendering for every selectable local mission, incremental
  decryption with a cached Android Keystore handle, coherent local/server evidence
  selection, and no synthetic trail when stored evidence is unreadable.
- GPS fixes are bounded to server-accepted coordinate, accuracy, and clock limits;
  secure capture/storage failures end tracking coherently, preserve a device-issue
  close, and surface a persistent field warning instead of silently stopping.
- Human supervisor review with three outcomes, required notes, optimistic versioning,
  exact context response linkage, and direct-review bypass removal.
- Per-person, per-session evidence-source selection with RLS-preserving server
  aggregates, exact-session route queries, local Room session isolation, receive-time
  and app-version provenance, reported accuracy ranges, and GPS/manual priority-visit
  context. Mission totals and the selected trail are kept as separate UI values;
  paged reads fail closed instead of silently accepting a PostgREST row cap.
- Meaningful observation/deviation/safety input, accurate end reasons, confirmation,
  and encrypted offline mutation outbox.
- Duty-window enforcement on device and server, five-minute autonomous close, sealed
  offline evidence window, correct local close timestamp, and post-sync local route
  deletion.
- Server-owned idempotent session start/end; direct authenticated session writes
  removed. One person/assignment is limited to 16 total evidence sessions and four
  newly created sessions per rolling 15 minutes; exact retries and recovery of the
  existing open session remain available at the cap.
- Server-owned bounded track/visit/update ingestion; direct authenticated evidence
  inserts removed; the 20,000 GPS-point ceiling spans every session for one
  mission/person; strict route-geometry and mobile-parser bounds.
- Least-privilege route/unit/mission control-plane permissions and atomic assignment.
- Encrypted token, snapshot, action, and route storage; account-owner binding; remote
  logout; map-cache cap/clear; release screenshot/overlay protection.
- Versioned pre-auth privacy notice and written deployment policy; fixed 365-day
  post-closure retention; server purge, legal-hold and aggregate deletion ledger;
  single-clock connectivity-independent device expiry for unsent evidence with
  crash-consistent privacy-safe integrity reporting; protected startup/capture gate,
  malformed or expired snapshot deletion, immediate app lock, and background timeout lock.
- API 36 target, stable AndroidX Security dependency, dedicated production package
  boundary, immutable-pinned CI actions, pinned Supabase CLI, CodeQL/dependency review,
  private-draft SBOM/checksum, and signing verification. Public GitHub provenance
  attestation is deliberately disabled because this public repository would expose the
  private APK digest.
- Permanent PatrolGrid version/package identity; public-policy/protected-main governance;
  GitHub-verified signed annotated release tags; isolated build/sign versus private-draft
  privileges; ciphertext-only inter-job transfer; verified direct-install helper; Room
  schemas v2–v7 and tested encrypted, data-preserving upgrade paths.
- Accessible single-target radio semantics, scroll/IME resilience, higher-contrast
  operational colors, compact/wide navigation selection, and removed dead destinations.

## Full-rollout blockers

### Code/product gates

1. **Abuse resistance.** Validated evidence-ingestion RPCs, strict GeoJSON bounds,
   batch/assignment/session and rolling-restart quotas, fail-closed mobile pagination,
   and review-visible user/session/method/accuracy/receive-time provenance are complete.
   Add backend minimum-build enforcement and server-verified
   hardware-backed device-key/request binding suitable for private sideload distribution.
   Integrity is a risk signal, never proof of physical presence.
2. **Supervisor MFA and managed reauthentication.** Password re-entry lock is built;
   AAL2/SSO or TOTP step-up for assignment/review and hosted session policy are still
   required.
3. **Data-rights operations.** The purge and legal-hold engine is implemented. The
   Department must still approve the legal/records schedule and provide an owned,
   tested workflow for access, correction, export, deletion, grievances, request
   identity verification, decisions, and dead-letter/escalation handling.
4. **Route and unit administration.** Assignment uses existing routes/units. A complete
   supervisor route library, geometry/priority editor, archive/history workflow, and
   approved managed unit/membership administration surface remain OPEN.
5. **Native matrix.** Finish tablet/foldable/landscape/split-screen, 200% font across
   every screen, TalkBack, RTL/long locale, denied/approximate/revoked permission,
   process-death, map gestures, and lower-end frame/startup benchmarks.

### Deployment/operations gates

1. Apply reviewed migrations to a clean hosted staging restore, then run the complete
   Auth/RLS/E2E/offline/reboot/load suite against staging with synthetic accounts.
2. Verify Supabase Security Advisor, SSL/network restrictions, owner hardware MFA,
   signup/anonymous controls, SMTP/recovery/rate limits, monitoring, region, and DPA.
   Configure every backup/PITR/WAL/export/replica copy to an approved maximum of no more
   than 30 days. Complete a network-quarantined restore drill that applies current
   migrations, reconciles hold releases, drains retention backlog, and proves expired
   unheld evidence cannot reappear before service is reopened. Unknown hosted retention
   settings or an untested restore remain a rollout blocker.
3. For private sideload rollout, never publish the GitHub draft: it contains only an
   encrypted unsigned candidate, never a signed APK. Use the separately audited,
   root-installed Mac launcher while the one-day Actions artifact still exists; it must
   byte-bind draft and artifact, decrypt, validate, sign offline, and atomically expose
   one owner-only bundle containing an exact three-file `staff/` subtree and a separate
   private `owner/` mapping subtree. Share only `staff/`. First install must be
   performed through the verified ADB/managed-device installer, or its APK hash and pinned
   signer must be delivered and verified through a second authenticated Department
   channel; an APK and checksum sent together are not first-install authentication.
   Require supported patched Android devices, screen lock, automatic time, remote session
   revocation, unique accounts, and a joiner/mover/leaver plus lost-device response
   procedure. MDM remains recommended if the subdivision later adopts it.
4. Record operational acceptance of OpenFreeMap's public-service terms,
   privacy handling, and lack of SLA (or operate the open-source stack on
   organization-controlled infrastructure). Publish the Department's exact legal name,
   official supervisor privacy/grievance channel, response period, and approved notice
   translations; approve the retention/legal-hold/export policy, staff training,
   incident response, and an independent MASVS/MASTG mobile/API penetration test and
   threat model.
5. Configure and exercise the retention operations SLO: every unheld/no-open-session
   mission must be physically deleted within 15 minutes after eligibility; alert when a
   scheduled job is late/failed, any deletable backlog remains after its drain, or the
   oldest backlog exceeds 15 minutes. A staging load run must prove capacity at least
   twice the projected five-minute peak and fail closed before rollout if the 2,000-per-run
   envelope or database headroom is insufficient.
6. Close the release trust gates: configure and approve the exact production backend and
   privacy notice; enable FileVault; independently audit/install the root-owned release
   bundle; verify two encrypted offline key/tool/recovery copies; run one signed-squash
   governance test PR; move signing to a dedicated offline account/device with per-use
   hardware-backed authorization; and implement an independent clean/reproducible build
   comparison before accepting a hosted-runner APK for signing. Temporary-file cleanup is
   logical deletion on APFS, not a secure wipe. No tag or release may be created before
   these gates have recorded owners and evidence.

## Rollout sequence

1. **Synthetic engineering QA** — current stage; no staff accounts or real patrol data.
2. **Controlled staging pilot** — small named group, non-disciplinary evaluation, MFA
   and documented device controls, approved notice/retention, privacy/grievance and
   incident drill, daily technical review. No separate technical-support desk is
   assumed.
3. **Subdivision rollout** — only after every blocker above has an owner, evidence, and
   signed acceptance; expand gradually with monitored rollback rather than a single-day
   all-staff launch.
