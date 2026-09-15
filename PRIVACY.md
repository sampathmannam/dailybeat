# DailyBeat privacy information — 4.1 public beta

Updated 2026-09-16. This describes the candidate source, not a claim about older installed releases.

## On your phone

Visits, approximate route points, notes, diary drafts, named/private places and review state are
stored in app-private storage. Cloud AI is optional and off by default on a new installation.
There is no account requirement for capture, notes, review, local drafts or search.

Android permissions and device power restrictions affect capture. You can deny location, pause it,
or turn it off. A pause does not delete earlier records. Android speech recognition may use a
network service selected by your device; type a note if you do not want to use it.

## Network services

- Standard builds use Google location services. The platform-only build excludes that SDK, but the
  device's own location providers are outside DailyBeat's control.
- Interactive maps contact map/tile providers, which can observe your IP address and requested map
  areas. Place lookup contacts the OpenStreetMap geocoder. Configured private zones are checked
  before reverse-geocoding; this is not an anonymity guarantee for map viewing.
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
The legacy-restore option reads older **unencrypted** snapshots. Old cloud copies remain until
separately deleted; making a new encrypted backup does not retroactively protect or remove them.

The snapshot includes private-zone definitions so protection survives recovery, but excludes
API keys, auth sessions, the recovery passphrase and dormant DSR data. Exported files and shared
copies are not included. Local database/backup deletion and cloud-account retention workflows must
be reviewed before general availability.

## Limitations

This app is not certified for evidence custody, attendance verification, emergency use, or continuous
perfect location coverage. Cloud citations do not verify the truth of generated prose. Physical
battery/recall trials, an independent security review, store disclosures and a production backup
recovery drill remain necessary before a broad release.

Algorithm reference: [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography).
