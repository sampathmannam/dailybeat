# Public beta: plan, implementation and release gates

Date: 2026-09-16

Branch: `feature/public-foss-readiness`

Baseline: `a839e8f` (adaptive capture); stable release marker 4.0.6.

Candidate: 4.1.0-beta.1 / code 28. The owner has requested a release; none has been published.
See [the latest follow-through](RELEASE_FOLLOWTHROUGH_20260916.md) for the approved GPL licence,
deployed additive backend migration and physical-phone tests. The remaining acceptance gates still apply.

Local QA results and known limits: [validation record](PUBLIC_BETA_VALIDATION.md).

## Product direction

One native phone app, three opt-in writing templates:
Personal (places and memories), Field work (visits and work notes), Police (officer details and formal draft).
Prioritise voluntary use by field engineers, service/sales professionals and inspectors, with personal
journaling available from the same core. Police users keep their existing template and data.

The promise is a reviewable location-assisted journal, not proof of activity or a tracking guarantee.
Keep Warm Butter and Carbon, current navigation and DSR separation.

## Implemented in this candidate

| Slice | End-to-end path |
| --- | --- |
| Templates | Onboarding → settings persistence → local/cloud prompt → PDF heading → backup/restore. Missing legacy profile decodes as Police; fresh installs start Personal. |
| Account-free core | Offline daily chronology and weekly source summary; existing local notes/review/editing remain usable without cloud credentials. |
| Local retrieval | Debounced on-device search of diary text, notes and visible visit labels; all history, at most 100 records. Notes-only days stay in Days. |
| Outbound privacy | Shared visit/note filtering; no old-prose reuse in weekly cloud inputs; conservative export copies; PDF/ZIP preview and source/settings invalidation. |
| Draft trust | PDF no longer implies submission; stronger sentence-citation coverage; explicit limitations; old unattended-generation setting needs renewed opt-in. |
| Repair safety | Renaming/hiding uses field-specific conditional SQL; clipped overnight timestamps and newer unrelated edits are preserved; stale renames fail visibly. |
| Encrypted recovery | JCA envelope → new backend table → passphrase UI → authenticated decode → existing validated transactional restore. Legacy recovery is explicit and uses its original table. |
| Google-free build | Separate GMS/platform location implementations; Google-free permission manifest; dependency gate and separate CI job. Standard production identity/signing rules retained. |

The public beta is an implementation milestone, **not completion of all T01–T22** in the larger roadmap.
Immutable corrections/diary revisions, statement-level evidence lineage, confirmed follow-ups, shortcuts,
state-aware coverage intervals and sustained field validation remain unfinished. Do not claim otherwise.

## Build / validation order

1. Run repository policy tests.
2. Build, lint and unit-test both location configurations.
3. Compile both instrumentation configurations; install only in the disposable test package on a
   dedicated emulator. Exercise first run, offline journal/search, sharing preview, native PDF/ZIP,
   crypto recovery, capture pause/resume, font scaling and existing navigation.
4. Review the dependency graph, merged manifest and artifact identity; package isolated QA builds.
5. In a separately approved backend rollout, apply the additive migration, execute owner-isolation
   tests and live envelope transport, then perform real encrypted/legacy Android recovery drills.
6. Validate on physical devices before creating a signed beta or stable release.

A local Docker daemon was unavailable at initial validation, so writing pgTAP tests does not mean
they ran. Never substitute static SQL checks for the live RLS gate.

## Distribution gates — do not publish before these decisions

- **Licence:** owner approved GPL-3.0; original source is now GPL-3.0-only with LICENSE included.
  Complete third-party compatibility/notice review for the exact distributed configuration.
- **Dependencies/assets:** audit all licences and origins, including MapLibre native artifacts,
  fonts and design assets. The no-GMS gate is narrower than a complete FLOSS audit.
- **Backend:** the approved encrypted table is deployed and CI transport checks now pass for both tables.
  Legacy user data was not changed by the migration. Still explicitly exercise
  recovery after a clean installation and wrong-passphrase handling on a dedicated QA account.
- **User control:** finish local/cloud deletion, retention and privacy-policy/contact disclosures
  before broad availability; never remove a user's old readable backup silently.
- **Security:** run dependency/secret scans and independent review of envelope/restore/export paths.
- **Battery/recall:** run the 14-day physical trial specified in the product roadmap, separately for
  Google and platform builds. Measure the same routes/devices, meaningful-stop recall, callback gaps,
  review time, CPU/wake time and battery against a capture-off baseline. No daily drain claim yet.
- **Publication/signing:** preserve the existing production key and update path. Decide how F-Droid
  signing/reproducible builds coexist with GitHub-installed users; no uninstall/downgrade workaround.
- **Store review:** prepare truthful descriptions/screenshots and disclose optional network services.
  F-Droid admission is a maintainer decision, not implied by this branch.

[F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/) requires a FLOSS licence,
acceptable dependencies and a verifiable source build. Start with a reviewed GitHub beta; Obtainium
can update releases but is not a curated store. Do not assume IzzyOnDroid eligibility.

## Larger roadmap sequence after this beta

1. Immutable source/correction and diary revision storage, explicit source manifests and review provenance.
2. Route repairs (manual/split/merge) and state-aware coverage reporting using those foundations.
3. Confirmed follow-ups and quick capture shortcuts; avoid inferring commitments from GPS.
4. Field trial and independent security/backup recovery sign-off.
5. Approved, signed release and store submission.

See [full task definitions and acceptance criteria](PRODUCT_IMPROVEMENT_PLAN.md).
