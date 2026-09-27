# Offline approximate place names

DailyBeat uses a bundled public town-name index when a captured stop has no usable saved name,
manual correction or address. It does not call a geocoding service, upload coordinates, change
captured history, or need downloaded map tiles. This is a display fallback, not venue detection.

Labels within 10 km of a listed settlement reference point read `Approx. area · Near Rasipuram`.
Farther away they read `Approx. area · About 85 km from …`, rounding to 5 km. Distances are
straight-line reference distances, not travelled distance. A nearby town is not a claim about
municipal boundaries, the country the user is in, a street, or a particular business. The nearest
listed town can be across a border. Small villages, streets and businesses are not comprehensively
covered. Saved names and actual addresses remain preferable; the existing naming action remains.
Without a valid GPS fix there is no defensible location name; the app still says no fix was recorded.

Both legacy coordinate fallbacks and new approximate labels remain unconfirmed. They cannot mask
actual addresses, prefill a saved-place name, or become a confirmed recurring venue in suggestions.
Only recognized generated moment text is repaired; user-authored prose is preserved.

## Data, licence and provenance

- Source: [GeoNames cities15000](https://download.geonames.org/export/dump/cities15000.zip),
  downloaded 2026-09-26. Format and licence: [official readme](https://download.geonames.org/export/dump/readme.txt).
- Copyright: GeoNames contributors. Licence: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
  This dataset retains its own licence; it is not relicensed as app code. Attribution is also
  visible in the app's Licences & Resources content. No GeoNames endorsement is implied.
- Archive SHA-256: `9c1f26fa632212ad77e5b82b6d5df3b019a3c119e423efe59d7224a186f9c9f4`.
- Retained: populated settlements with feature codes PPL, PPLA, PPLA2, PPLA3, PPLA4, PPLA5 or PPLC.
  Excludes neighbourhood-only, abandoned and historical entries. Uses the source's transliterated
  name where available, source country, and coordinates converted to 3D unit vectors.
- Result: 31,638 settlements, 908,531 compressed bytes. No user data.
- Resource SHA-256: `61e82640f44c0cf84bfd13cf1c74a21134d6c0ef84066cd37bd70c129e213929`.
- The reviewed DBT1 extract (SHA-256 `3a36460fd221f2254fd533cf8ef2686f55a386e401abd82da29bd89f32f1f49c`)
  is retained under `data/reviewed/`; DBT2 rearranges its exact vector and label bytes for bulk load.
- GeoNames offers no warranty of accuracy, completeness or timeliness. No periodic network updater
  or background location work is added. Future source changes require review and a new pinned hash.

## Reproduce and validate

```sh
python3 scripts/build_offline_towns.py data/reviewed/offline-towns-v1.dat.gz \
  android/app/src/main/resources/offline-towns-v2.dat.gz --legacy-index --check
```

The original pinned GeoNames archive can also be passed without `--legacy-index`; both routes
produce the same DBT2 bytes. Remove `--check` only to regenerate the reviewed resource.
Conversion uses Python standard-library
ZIP, gzip and numeric primitives; there is no download or remote execution. It refuses any source
with a different hash. The gzip timestamp and filename are fixed. Runtime code is original Kotlin,
not imported upstream executable code. The resource contains a versioned, length-bounded binary
index; its compressed bytes are checked against the pinned SHA-256 before any vector is used.
Malformed, oversized or missing data cannot crash label rendering or expose raw coordinates.

The balanced 3D kd-tree is constructed at conversion time. Android authenticates the immutable
index and bulk-loads its contiguous vectors once per process, warmed on an IO dispatcher at
Application startup. Names decode only for actual nearest results. A caller arriving before warmup finishes
waits for that same initialization rather than retaining a transient placeholder. Lookup
uses chord distance, preserving great-circle order at the date line and poles. No personal lookup
cache, network request, database write, timer, wake lock or additional GPS sampling is involved.

Tests compare the index with an independent exhaustive great-circle calculation, verify resource
hash/count, check invalid/corrupt input and legacy labels, and exercise Today/Diary/Days naming with
network features off. Phone timings measure repeated fresh resource decodes and synthetic lookups;
they are not cold-process or all-day battery measurements.
