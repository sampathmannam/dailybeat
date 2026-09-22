# Managed place lookup contract

Automatic reverse geocoding is off by default. Saved place names and unnamed local stops continue
to work. No automatic production request is sent to the shared public Nominatim endpoint.

For an organisation that needs automatic names, configure an HTTPS Nominatim-compatible reverse
endpoint in Settings. The URL must contain no credentials, query or fragment. It can be changed or
cleared at runtime without distributing another APK. The chosen endpoint receives lat/lon query
parameters only for locations outside all saved private zones. Local privacy-rule read failures
abort outgoing lookup. Cache entries are scoped to the endpoint. Redirects are refused, response
size is bounded to 1 MiB, and call duration is bounded to 20 seconds.

Deploy a self-hosted service or contract with a provider whose terms permit automatic mobile
reverse geocoding. Put aggregate quota/rate enforcement, service monitoring and any provider
credentials at that endpoint. The mobile app's local throttle does not enforce fleet-wide quotas.
Require a retention policy for request logs: do not log full coordinate-bearing URLs by default.
Test the endpoint with synthetic coordinates and verify that the provider never receives private
zone requests. This repository does not provision a paid provider or make an availability promise.

The configured endpoint is deliberately excluded from backup: enabling a new phone should require
an explicit choice of where future location requests go. Online map tiles remain a separate network
feature with their existing attribution and privacy disclosure.

## Place names are candidates, not proof of entry

[Nominatim reverse lookup](https://nominatim.org/release-docs/latest/api/Reverse/) returns the
nearest suitable mapped object, which can belong to another street or business. Its returned
coordinate is an [object centroid](https://nominatim.org/release-docs/latest/api/Output/), not
the user's indoor position. A road address or a venue absent from OpenStreetMap cannot establish
which shop was visited.

DailyBeat accepts a named, classified POI only within a conservative 50-metre centroid distance
and keeps the label qualified as **Near …**. This distance is a product heuristic, not a calibrated
confidence or proof of entry; neighboring businesses and large-building centroids remain ambiguous.
Feature names take precedence over chain brands, and a legal operator alone is not treated as a
venue name. Roads and administrative features cannot masquerade as POIs when an endpoint ignores
the requested layer. The address lookup is a fallback, not an exact venue identification.

Name the stop yourself to save the exact label locally for later visits, including offline use.
Existing manual names and recorded history are not rewritten. The v3 cache does not reuse the old
200-metre exact-name guesses and refreshes lookup entries after 30 days. Frequent-place proposals
remain opt-in suggestions: hidden/invalid stops and stops inside saved places are excluded; distant
or conflicting venue names are not merged into a new midpoint location.

The shared public service is not an automatic lookup backend for this app. Its
[usage policy](https://operations.osmfoundation.org/policies/nominatim/) includes application-wide
limits, caching requirements and restrictions on periodic queries. Use only a deliberately
configured managed endpoint whose terms permit this workload; offline/manual behavior is unchanged.
