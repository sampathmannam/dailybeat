# DSR Command dashboard

## Purpose

DSR Command converts each uploaded Rasipuram subdivision operational PDF into a dated, queryable snapshot. The first release supports:

- Daily DSR reported-case rows, station progress totals, advance forecasts and selected pending-work totals.
- All-crime first-page aggregates by year/YTD period, station and crime head, including detection, stage and property recovery.
- TASMAC subdivision inventory totals.
- Fatal-accident report totals and report-period validation.
- Same-file idempotency and same-date revisions.

## Import contract

1. The Android Storage Access Framework supplies a PDF URI; the app does not request broad file access.
2. The document is copied to app-private storage with a 25 MB limit, checked for a PDF signature and named by SHA-256.
3. PDFBox extracts digital text page by page on the device. Raw text is not persisted in Room.
4. Deterministic parsers detect the report type, normalise station aliases and extract operational fields.
5. Validation runs before a database transaction commits the snapshot.
6. Uploading identical bytes is a no-op. Uploading different bytes for the same report type and date archives the earlier active version and preserves its audit record.

## Privacy boundary

The structured case table intentionally stores only station, crime number/year, case head, sections of law, priority and source-page reference. It does not store names, phone numbers, caste/community, street addresses, medical narratives or the gist of the case. Source PDFs remain in Android app-private storage and are not part of DailyBeat's Supabase backup payload.

All-crime statements receive a stricter treatment: only the aggregate station and crime-head tables on page 1 are parsed. Later case-detail pages are ignored by the structured importer.

The import path never sends a DSR to the diary app's configured cloud LLM. This is a hard separation from DailyBeat's optional cloud diary generation.

## Metric semantics

Every metric is labelled as one of:

- `DAILY`: work performed or received on the report date.
- `PENDING`: balance at the date of the snapshot.
- `CUMULATIVE`: a period-to-date figure.
- `INVENTORY`: an asset count at the snapshot date.

The dashboard never adds `PENDING`, `CUMULATIVE` or `INVENTORY` snapshots to produce a daily total.

## Validation rules in the first release

- Filename date versus in-document date.
- Duplicate PDF hash.
- Corrected report revision for the same type/date.
- Station aliases such as Namagiripet/Namagiripettai, Mangalpuram/Mangalapuram and AWPS RPM.
- Conflicting case heads or sections against the same station/crime number/year.
- Parsed reported-case count versus station-progress total.
- Partially unreadable station-progress rows.
- All-crime station arithmetic, crime-head totals and printed detection/recovery percentages.
- Full-year versus year-to-date labelling; trend bars use cases per covered month.
- TASMAC inventory arithmetic and detailed/summary Patta Book disagreement when all detail rows are readable.
- Fatal-accident heading month versus occurrence-date month.
- Image-only or very-low-text documents.

## Known boundary

Digital-text PDFs are imported automatically. Image-only/scanned pages are deliberately flagged instead of guessed. An offline OCR review flow can be added later, but must retain the same verification gate because OCR output is not authoritative.
