# DailyBeat privacy information — 4.3.4

Updated 2026-09-21. This describes the candidate source, not a claim about older installed releases.

## On your phone

Visits, approximate route points, notes, diary drafts, named/private places and review state are
stored in app-private storage. Cloud AI is optional and off by default on a new installation.
There is no account requirement for capture, notes, review, local drafts or search.

New PDF/ZIP exports and diagnostics use internal app storage. Only the exports directory is
available to Android's sharing provider; diagnostics are outside it. Upgrade moves older generated
exports from app-specific external storage into internal storage where accessible. Erasure also
clears known older output locations and interrupted export files. Copies already shared with other
apps, or unavailable on disconnected storage, remain outside that operation.

Android permissions and device power restrictions affect capture. You can deny location, pause it,
or turn it off. A pause does not delete earlier records. Android speech recognition may use a
network service selected by your device; type a note if you do not want to use it.

## Network services

- Standard builds use Google location services. The platform-only build excludes that SDK, but the
  device's own location providers are outside DailyBeat's control.
- The F-Droid store build uses the platform-only backend and contains no advertising or analytics
  SDK. It does not include the developer's managed cloud-backup configuration. Notes, local drafts,
  review, capture, maps and PDF/ZIP sharing remain usable without an account.
- **Allow online maps** is on by default, including upgrades. Online thumbnails and interactive maps
  contact map providers, which can observe your IP address and requested areas, styles, fonts and
  sprites. Turn it off in Settings → Capture & Places to cancel automatic map requests. With an
  installed Tamil Nadu package, full maps use local street detail inside its coverage. Otherwise,
  the recorded route remains available. Free online providers do not offer guaranteed availability.
- **Download Tamil Nadu** is a separate, explicit internet action, even when online maps are off.
  GitHub and its delivery network receive your IP address and package requests, not your recorded
  route. Downloads default to unmetered Wi-Fi; mobile data requires a separate choice. All files
  are verified against hashes in the signed APK before activation. Fonts, sprites and styles are local.
- Automatic address lookup is separately controlled and off unless you configure a supported HTTPS
  geocoder and enable it. Configured private zones are checked before reverse-geocoding; this is
  not an anonymity guarantee for online map viewing. The package provides no offline address search.
- If you enable Cloud AI, your configured provider receives selected times, place labels and note
  text. Coordinates are not added by the timeline builder, but notes themselves can contain
  coordinates or identifying information. Provider retention rules apply to sent content.
- Optional cloud backup sends an encrypted snapshot to your configured backend. It still exposes
  account identity, timing and ciphertext size. It does not provide anonymity or rollback protection.

## Private zones, hidden stops and sharing

Private-zone visits and hidden stops are omitted from outbound source timelines. Notes are also
withheld when recorded within a private coordinate, during a withheld stop, or mentioning a known
restricted label. These conservative rules can omit innocent notes and cannot detect every sensitive
paraphrase. They are not a general-purpose secrecy classifier.

Stored prose lacks reliable source lineage. While any private zone or hidden stop exists, PDF/ZIP
sharing rebuilds a copy from current filtered records instead of exporting old prose. The original
diary is retained locally. Weekly cloud drafts also use filtered sources instead of older diary text.
Unlinked custom text and voice enrichment are kept local when these controls are active.

The preview displays the outgoing diary text, author, supervisor (Police only), template and dates.
Sharing is invalidated when the underlying records or settings change during preparation/rendering.
The ZIP contains the previewed text and PDFs, not diagnostic logs. Review notes yourself before
sharing. Once another app receives a file, later privacy changes cannot recall that copy.

## Backup encryption and legacy copies

New backups use AES-256-GCM, random 16-byte salt and 12-byte nonce, and PBKDF2-HMAC-SHA256
(600,000 iterations). A 20–256 character recovery passphrase is required; six random words are
recommended. It is separate from the account password, held only for the operation, and not uploaded
or stored in preferences. Memory clearing is best effort on Android/JVM; this does not protect a
compromised unlocked device.

Encrypted and legacy backups use different tables. Wrong keys, modified ciphertext and unsupported
envelope formats fail before database restore. There is no automatic downgrade to legacy recovery.
The legacy-restore option reads older **unencrypted** snapshots. Making a new encrypted backup does
not retroactively protect or remove them. A signed-in user can delete both cloud-backup formats in
Settings, or delete the cloud account after entering the current password again. Account deletion
uses a server-side function; no service credential is included in the app.

The snapshot includes private-zone definitions so protection survives recovery, but excludes
API keys, auth sessions, the recovery passphrase and dormant DSR data. Exported files and shared
copies are not included. Map preferences, provider overrides, downloads and map caches are
also excluded: cloud restore does not change this phone’s map-network choice. The selected local-retention period is restored with the encrypted backup;
older restored history is pruned immediately when that setting has a finite period.

Restoring any backup preserves this phone's Cloud AI provider, model and server address, and
disables Cloud AI until you enable it again. A backup cannot authorize sending this phone's API
key to an imported server. Queued cloud requests recheck the configured destination, cloud consent
and API key immediately before transmission; information already sent cannot be recalled.

Local history is kept until deletion by default. A user may instead choose 30 days, 90 days or one
year. Applying a shorter period deletes complete older days immediately and a daily background task
continues the policy. It covers events, visits, route points, diaries, day reviews, diary versions
and visit-correction history; named-place definitions and settings remain. Cloud backups are a
separate copy and are not shortened by local retention. “Erase all data on this phone” removes the
local database, settings, exports, API key, saved cloud session, map packages and caches;
automatic maps remain off after erasure after typed confirmation; it does
not delete cloud data unless that separate action is used first.

## Limitations

This app is not certified for evidence custody, attendance verification, emergency use, or continuous
perfect location coverage. Cloud citations do not verify the truth of generated prose. Physical
battery/recall trials, an independent security review and store disclosures remain necessary before
a broad release.

Algorithm reference: [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography).
