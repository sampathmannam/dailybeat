# Place-name coverage decision — 25 September 2026

## Decision for the current fix

Keep MapLibre and the existing offline Tamil Nadu map. Do not silently switch providers,
enable coordinate uploads, embed a paid API key, or claim that a nearby business is the
place the user entered. No alternative's Namakkal venue coverage has been field-verified
in this investigation, including Royal Oak. Documentation establishes available options,
not their accuracy at a particular stop.

Map rendering and stop naming are separate in DailyBeat:

- MapLibre renders the vector/offline map and our recorded route.
- `OsmGeocoder` optionally calls a configured, managed Nominatim-compatible endpoint.
  Its default endpoint is empty, so installing a map does not enable online place lookup.
- Saved place names, recorded addresses, and user corrections are local naming sources.
- Map-derived nearby venues retain a “Near” qualifier. Where no usable name/address is
  available, the display uses rounded approximate coordinates rather than “Unnamed place”.
  Recorded history and authored notes are not rewritten merely to improve a label.

## Alternatives checked

| Option | Potential benefit | Limitation / decision |
| --- | --- | --- |
| **Overture Places alongside MapLibre** | Additional business POIs from Meta, Microsoft, Foursquare and other sources, not just OSM; a regional extract can be queried locally. | Best open-data candidate to evaluate next. It is a dataset, not a drop-in Android search service. Needs a regional spatial index, updates, source notices, and local coverage/accuracy measurements. Not integrated or claimed more accurate yet. |
| **Geoapify Places** | Managed category/nearby search over OSM data. | OSM is its primary Places source; changing to it alone is not evidence that an OSM-missing shop becomes available. Account, quota and location-sharing review required. |
| **Mappls nearby search** | An India-focused proprietary POI/search alternative. | Requires credentials and agreement on the offered fields/use case. Actual local coverage, price, retention rights, MapLibre display rights and any SDK/store impact must be checked before adoption. Not enabled. |
| **Google Places** | A separate commercial business/place database to evaluate if the product changes direction. | Not a drop-in source for this offline journal: Google's policy restricts content storage and requires Google Maps when displaying Places results on a map. Do not copy Google listings into the persistent MapLibre map/history. Not enabled. |

## Acceptance gate before adding a source

1. Benchmark a user-approved set of public venues (shops/restaurants plus adjacent roads,
   multi-tenant buildings and deliberately absent venues) around Namakkal and nearby towns.
   Use public reference locations; do not upload private diary coordinates for research.
2. Compare name availability, correct location/entrance, stale or closed listings,
   duplicates, and false venue matches against the current lookup. Report denominators,
   not a claimed “Google-like” accuracy score without measurements.
3. For an Overture prototype, download a versioned regional **places-only** extract,
   preserve feature IDs/source/license notices, validate archive size/checksum, and build
   an indexed local lookup separate from the user's history database. Benchmark package
   size, query latency, memory and update cost before bundling it.
4. Query around an observed stop, not on every GPS tick. Respect accuracy radius, dwell
   duration, private zones, and competing candidates. A nearby POI is a suggestion, not
   proof of entry; let the user confirm a saved name once for future local reuse.
5. Prefer confirmed saved name, then a qualified nearby candidate/address, then approximate
   coordinates. Preserve route capture and meaningful fallback labels when offline or a
   source is absent. Never invent a business to make an empty result look complete.
6. Any new online provider must be explicitly optional, bounded/time-limited, cancellable,
   credential-safe, and documented in privacy/attribution screens. Existing offline and
   private-zone behavior must pass regressions before shipping.

## Primary sources checked

- [Overture Places guide and source coverage](https://docs.overturemaps.org/guides/places/)
- [Overture per-source attribution and licensing](https://docs.overturemaps.org/attribution/)
- [Geoapify: OSM is the primary Places API source](https://www.geoapify.com/tutorial/how-to-get-osm-places-by-category/)
- [Mappls Nearby API parameters and access qualifications](https://developer.mappls.com/documentation/sdk/rest-apis/mappls-maps-near-by-api-example/Readme/)
- [Google Places storage, attribution and map-display policies](https://developers.google.com/maps/documentation/places/web-service/policies)

Provider terms/data change. Recheck them for the chosen release and distribution model;
this technical comparison is not a guarantee of licensing clearance or venue accuracy.
