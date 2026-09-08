# DSR Command: reliability and expansion roadmap

## Product objective

DSR Command should turn an operational PDF into a trustworthy, source-linked command brief. It
must help a supervisor answer four questions quickly:

1. What changed since the previous report?
2. What needs action today, by station and priority?
3. Which extracted facts require human verification?
4. Can every important number or case be traced back to the source PDF?

The current dashboard already provides a useful offline base: immutable source-PDF storage,
hash-based duplicate protection, same-date revision handling, cases, station metrics, pending work,
forecasts, quality checks, and import history. The next work should deepen trust and command
workflow before adding more charts.

## P0 — Make every result reproducible and correctable

### Versioned parsing and safe reprocessing

Store a `parserVersion`, extraction-engine version, parsed-at time, and result checksum with each
import. When a parser fix ships, show that an older report can be reprocessed from its private
original without asking the user to locate the PDF again.

- Reprocessing creates a new immutable parse result; it never overwrites the previous result.
- Present a before/after diff for cases, metrics, forecasts, and warnings.
- Let the user activate the new result or retain the previous one.
- Keep the original file hash as the document identity and the parse-result ID as a separate
  version identity.
- Run the job in cancellable background work and recover cleanly after process death.

This closes the most important current gap: hash deduplication correctly blocks a duplicate file,
but also means an already-imported report does not automatically benefit from later parser fixes.

### Source-first verification

Every case, metric, forecast, and warning should carry provenance rather than only some warnings.

- Store source page and, when extraction supports it, bounding box or table cell coordinates.
- Add **Open source** on a card to open the private PDF at the relevant page.
- Highlight the cited row or paragraph when coordinates are available.
- Show the extracted text beside the normalized value for disputed fields.
- Never imply precision when the source location is unknown.

### Explicit confidence and blocking rules

Replace a single document-wide quality percentage with explainable section confidence.

- Distinguish blocking errors, review warnings, and informational notes.
- Show the exact deductions that produced a quality score.
- Prevent a report from becoming the active command view when required identity fields are absent
  or contradictory: report type, date, subdivision, or station mapping.
- Allow partial sections to remain visible, clearly marked as incomplete.
- Provide a **Review complete** acknowledgement with actor, time, and optional note; do not delete
  the original warning.

### Import transaction and concurrency guarantees

- Preserve exactly-once behavior for identical PDFs, including simultaneous imports.
- Ensure a failed extraction leaves neither an active database record nor an orphaned private file.
- Make same-date corrected reports deterministic under concurrency.
- Add file-size, page-count, decompression, and processing-time limits with clear failure messages.
- Detect encrypted, malformed, image-only, and mixed-orientation PDFs before parsing.

## P1 — Turn the dashboard into a command workflow

### Start with an action brief

The first screen should prioritize action over document structure:

- **Critical now:** high-priority cases, major law-and-order forecasts, overdue warrants, and data
  contradictions that could change a decision.
- **Pending work:** e-Summons, e-Sakshya linkage, SID linkage, NBWs, and configurable operational
  backlogs with change from the previous report.
- **Today and next 72 hours:** forecast events ordered by date, priority, crowd estimate, and
  station.
- **Station exceptions:** stations with unusually high reporting, low disposal, missing cells, or
  worsening backlog.

Each action should support owner, due time, status, acknowledgement, and a source link. DailyBeat
can remain offline-first by storing workflow state locally and including it in encrypted backup.

### Search, filter, and navigation

- Search by crime number, year, station, legal section, category, or free text.
- Filter by report date, station, priority, section, and verification state.
- Add a sticky section navigator with counts so long reports remain manageable.
- Preserve filter and scroll position when opening a case or source page and returning.
- Offer **Only changed**, **Only needs review**, and **Unacknowledged** shortcuts.

### Comparison and trends

For daily metrics, compare with the previous active report. For cumulative or inventory metrics,
show deltas only when the semantics and reporting period match.

- Reported versus charged/disposed cases by station.
- e-Summons received, served, and pending movement.
- e-Sakshya recorded, linked, and pending movement.
- MV enforcement, NBWs, convictions, acquittals, and taken-on-file movement.
- New, continuing, corrected, and missing cases across report revisions.

Flag resets, negative deltas, and structural changes instead of presenting them as real operational
movement.

## P2 — Expand the extraction model

Add new sections only with a representative fixture set and source provenance:

- accused, victim, missing-person, arrest, remand, bail, and property/seizure facts;
- occurrence, reporting, registration, and disposal timestamps;
- grave crime, POCSO, prohibition, accident, cybercrime, and special-unit classifications;
- patrol, bandobust, VIP movement, protest, festival, and crowd forecasts;
- wanted persons, rowdy/history-sheet surveillance, NBW execution, and summons service;
- investigation stage, charge-sheet status, court outcome, and ageing buckets;
- station staffing or resource exceptions when present in the source format.

Normalize legal sections into structured codes while retaining the source wording. Maintain an
alias table for station names/codes and surface unknown aliases for review rather than guessing.

## P2 — Handle real-world PDF variation

- OCR image-only pages locally when feasible; clearly label OCR-derived values.
- Support mixed Tamil/English text without translating names or legal citations implicitly.
- Detect repeated page headers and footers before joining wrapped text.
- Build layout profiles by report type and issuing unit, with a conservative generic fallback.
- Parse tables using geometry when available, not whitespace alone.
- Retain blank versus zero as distinct states.
- Stream large files and bound memory use; expose progress by extraction, parsing, validation, and
  commit stages.
- Keep all parsing offline by default. Any future cloud-assisted extraction must be explicit,
  redacted where possible, and disabled unless configured by the user.

## P3 — Briefing and export

- Generate an offline deterministic command brief before offering AI wording.
- Export a source-cited PDF/CSV bundle with parser version, import hash, active revision, quality
  state, and acknowledgement log.
- Provide station-specific briefs and an overall subdivision brief.
- Redact personal data by export profile; preview exactly what will leave the device.
- Preserve the immutable original and never treat an exported summary as the source of truth.

## Test and evaluation programme

### Golden corpus

Maintain de-identified or fully synthetic PDFs covering every supported layout. For each fixture,
assert document identity, cases, metrics, forecasts, source pages, warnings, and quality result.
Include corrected revisions and deliberately ambiguous rows.

### Mutation and adversarial cases

- repeated headers/footers, wrapped station names, split tables, duplicate pages;
- same crime number in different stations or years;
- same month but different year in a report title;
- blank, zero, negative, grouped, and OCR-confused numbers;
- reordered pages, rotated pages, mixed page sizes, and truncated files;
- simultaneous identical imports and simultaneous corrected revisions;
- cancellation, low storage, process death, and restart during every import stage.

### Acceptance gates

- Zero silent field invention: unknown stays unknown.
- 100% provenance coverage for command-critical fields.
- Identical import remains exactly once under concurrency.
- Failed import leaves no active partial result or unreferenced source file.
- Parser upgrades are reprocessable and reversible.
- Accessibility traversal, large text, screen-reader labels, and long-list performance pass on a
  physical low/mid-range Android device.
- Production and QA data remain isolated throughout automated testing.

## Suggested delivery sequence

1. Add parser-result versioning and safe reprocess/diff.
2. Add source-page navigation and provenance to every command-critical field.
3. Add search, filters, sticky section navigation, and acknowledgement workflow.
4. Add previous-report deltas with metric-semantics guards.
5. Expand one extraction section at a time, gated by golden fixtures and field-level accuracy.
6. Add deterministic cited briefing/export, then optional AI wording as a separate layer.

The practical success measure is not the number of extracted fields. It is how quickly a user can
reach the source, identify what changed, take an action, and know which facts still require review.
