# Tamil Nadu maps

Open **Settings → Capture & Places → Maps**, then **Download Tamil Nadu**. Online maps are on
by default; switching **Allow online maps** off cancels automatic styles, tiles, fonts and sprites.
The recorded route remains available. Full maps prefer installed coverage; outside Tamil Nadu,
DailyBeat uses the configured online provider when enabled, or displays the route and coverage message.
Thumbnails use the existing safe Canvas renderer and remain route previews when online maps are off.
Open the full map for offline street detail.

Downloads are separate from the APK and use the internet even when online map viewing is off.
The current package is about 200 MiB; the exact size and map-data date appear before download.
Wi-Fi is the default. Choose mobile data explicitly if needed. Progress, pause/resume, retry, update
and deletion controls are on the same screen. Android may delay work until the chosen network and
storage are available. Keep additional space for unpacking, a 100 MiB safety reserve, and the old map
during updates. An interrupted transfer resumes without replacing the working version. New data
versions are announced through app updates, whose signed catalogs bind all download sizes and hashes.

The package covers the full Tamil Nadu boundary at zooms 0–15, with available OpenStreetMap streets,
labels and buildings. Zooming further enlarges that data; it cannot add unmapped features. Coverage
uses the polygon, including coastal and boundary areas. This is a basemap, not offline address search,
turn-by-turn navigation, live traffic or an authoritative legal boundary. Map dates describe the source
snapshot, not a guarantee that every road is current. OSM detail varies locally.

## Privacy and providers

Map preferences, provider overrides, caches and downloads stay on this phone and are excluded from
diary/cloud backups. Restore cannot change the phone's map choice. Local data erasure stops map work,
removes downloaded/staged maps and caches, and leaves automatic map traffic disabled.
Geocoding remains separately controlled and off unless configured and enabled.

The default online style is OpenFreeMap and thumbnails use OSM's standard raster tiles. Advanced
settings accept HTTPS style/raster endpoints plus attribution. Providers can observe requested map
areas and IP addresses. Free services have no availability guarantee. Downloads contact GitHub's
release delivery network with package requests, never the recorded route. No paid service is required.

## Package provenance and release

`scripts/maps/sources.json` pins the Protomaps planet snapshot, geoBoundaries/DataMeet Tamil Nadu
polygon, go-pmtiles tool hashes, font/sprite commit and basemap style package integrity.
`scripts/build_offline_map.py --work-dir /path/outside/repo --write-catalog` creates a new package;
review the catalog changes before building an APK. Normal CI/release runs omit `--write-catalog`
and reject any mismatch. `--prefer-published` verifies and reuses existing immutable release files
when available, so future app releases need not depend on a daily upstream snapshot remaining online.

The builder extracts the region from Protomaps PMTiles; it never bulk-downloads public OSM raster
tiles. Files are split at 512 MiB, with part and complete archive hashes. Compatible local styles,
sprites, complete supplied font glyph ranges, boundary and licenses are packaged separately. The
resource ZIP uses stored entries for identical output across build platforms. The APK embeds the
catalog. A foreground WorkManager download verifies every part before atomic activation. Open maps
hold leases so deletion/update cleanup cannot remove a file still in use by the renderer.

The existing signed release pipeline publishes the APK and map assets together, refuses to replace
existing releases, and checks the permanent signing certificate. Native tests render actual PMTiles
roads and labels at Chennai, Rasipuram, Namakkal, Coimbatore, Madurai, Kanyakumari, Hosur,
Nagapattinam and Rameswaram, in light/dark themes with no HTTP requests. Unit tests cover streaming
limits, cancellation, resume, corrupt downloads, low storage, rollback, deletion leases and restart.

Attribution: OpenStreetMap contributors (ODbL 1.0), Protomaps (BSD-3-Clause/CC0), Noto fonts (OFL),
Mapzen sprites (MIT), and DataMeet/geoBoundaries boundary (CC BY 2.5 India). See package licenses.

For the full-package device gate, copy the built archive and resource ZIP into the disposable test
app's private `files/full-map` directory as `tamil-nadu.pmtiles` and `resources.zip`. Run
`NativeOfflineMapTest` with instrumentation argument `fullMapDirectory` set to that absolute device
path. It verifies activation and deletion while the actual journey map is open, in addition to the
nine-location native rendering checks. Without that argument, CI uses the smaller regional viewport
fixture for rendering and the full-package activation test is skipped. Never seed a user's diary app.
