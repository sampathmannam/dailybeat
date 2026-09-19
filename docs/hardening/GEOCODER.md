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
