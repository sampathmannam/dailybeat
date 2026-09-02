# PatrolGrid staff privacy policy

- **Notice version:** 3
- **Policy date:** 2 September 2026
- **Retention period:** 365 days from first post-patrol review or closure in the
  live/usable service; bounded disaster-recovery copy handling is disclosed below
- **Status:** Deployment draft — department and legal approval are required
  before real staff or operational data is used.

This policy explains the intended PatrolGrid deployment in plain language. It is
not a statement that the app or a particular deployment complies with every
applicable law. The deploying department remains responsible for approving the
purpose, legal authority, hosting, access, retention, legal-hold, request, and
incident procedures before rollout.

## 1. Who is responsible

The department or police subdivision that issues the staff member's PatrolGrid
account and patrol assignment (the **Department**) determines why and how the
staff data described here is processed.

There is no separate PatrolGrid technical-support desk. The staff member's
subdivision supervisor is the privacy and grievance contact, using the existing
official department channel for administrative requests. That may be an official
department email, the station or subdivision correspondence process, or another
formally approved channel already communicated to staff. The published
deployment copy of this policy must identify the exact Department and the
approved official channel; a personal telephone number or personal email address
must not be substituted.

For an emergency, immediate safety issue, live patrol direction, access outage,
or other operational matter, use the Department's normal chain of command,
radio, or official telephone procedure. The privacy-request route is not an
emergency or operational response service.

## 2. What PatrolGrid processes

Depending on role and activity, PatrolGrid may process:

- **Account and work identity:** authentication account identifier and email,
  display name, badge number where supplied, role, subdivision, unit,
  membership status, and account/session records.
- **Assignment information:** mission title, instructions, duty window, assigned
  personnel, route and priority-location coordinates, acknowledgements, and
  mission status.
- **Active-patrol evidence:** session and installation identifiers, app version,
  patrol start and end times and end reason, precise latitude and longitude,
  location time, accuracy and sequence, priority-location visits, and any manual
  visit context.
- **Operational context:** observations, operational deviations, safety-event
  notes, responses requested during review, supervisor outcomes and notes, and
  related timestamps.
- **Integrity and security records:** event type, actor and mission identifiers,
  timestamps, structured audit metadata, authentication activity, and limited
  technical/network logs needed to secure and operate the service.
- **On-device copies:** encrypted route points awaiting upload, encrypted pending
  field updates, and a short-lived encrypted mission snapshot used for safe
  retry and temporary offline continuity.

Staff should enter only operational facts needed for the mission. They should not
add unrelated personal, family, health, or third-party information to free-text
notes.

## 3. When precise location is collected

PatrolGrid begins collecting precise route points only after patrol personnel tap
**Start patrol** for an assigned mission. Collection stops when the patrol is
ended or the configured duty deadline is reached. Evidence already recorded may
finish uploading after tracking stops; that upload does not continue location
collection.

PatrolGrid is not intended for off-duty, always-on, or covert staff tracking. The
Department must not use this deployment to extend collection outside an assigned
mission.

## 4. Why the data is used

The permitted purposes are to:

- issue and acknowledge patrol assignments;
- show the assigned route and priority locations;
- record what route was covered during an active patrol;
- support operational coordination, safety context, and an accountable handover;
- let an authorized supervisor review route evidence, GPS gaps, and staff
  explanations; and
- protect account, evidence, and audit integrity and investigate misuse or a
  security incident.

Coverage indicators, GPS gaps, and route differences are review aids. They are
not automatic proof of misconduct. PatrolGrid must not automatically discipline,
rank, score, or make a misconduct finding about staff. Any employment or
disciplinary use requires an authorized human decision under the Department's
applicable service and disciplinary procedures, with the staff member's context
considered.

Acknowledging the in-app notice only records that the notice was shown. It does
not waive privacy, employment, grievance, or other legal rights, and it is not a
substitute for the Department documenting its lawful authority to deploy the
system.

## 5. Who can see or receive the data

- Patrol personnel can see the missions and evidence made available to their
  authorized account.
- Authorized supervisors can see staff identity, assignments, planned and
  recorded routes, priority visits, field updates, review context, and outcomes
  within their subdivision.
- A minimum number of authorized Department administrators may access data when
  necessary for account administration, security, retention, legal hold, or an
  approved request or investigation.
- The configured Supabase service and its infrastructure providers process
  authentication, database, and network information on the Department's behalf.
  The Department must approve the production project, processing terms, region,
  subprocessors, and access controls before rollout.
- Information may be disclosed where an applicable law, court order, authorized
  investigation, or approved Department procedure requires it. Access or
  disclosure must not be broader than necessary for that purpose.

Role and subdivision controls are intended to prevent staff from viewing another
subdivision's data. They do not replace periodic access review, immediate account
revocation, or audit by the Department.

## 6. Open-source map disclosure

The Android app uses the open-source MapLibre renderer with OpenFreeMap's public
map service and OpenStreetMap data. When a basemap is requested, OpenFreeMap and
its content-delivery provider receive the requested map area and ordinary network
request metadata. OpenFreeMap states that regular server logs do not store IP
addresses, that temporary IP logging may be enabled for up to 30 days during a
security incident, and that Cloudflare may process requests.

The assigned and recorded route overlay, staff identity, and observations are
drawn on the device and are not intentionally sent to the map service. A route
can still be shown in a tile-free fallback if the public basemap is unavailable.
The public map service is provided as-is and may change or become unavailable.

Current provider documents:

- [OpenFreeMap privacy policy](https://openfreemap.org/privacy/)
- [OpenFreeMap terms of service](https://openfreemap.org/tos/)
- [MapLibre Native](https://maplibre.org/projects/native/)

## 7. Retention, deletion, and legal hold

Patrol evidence and its linked mission, visit, field-update, review, and audit
records are scheduled for deletion **365 days after the retention clock starts**.
The clock starts when a mission first enters a post-patrol review or closed state:
**needs review, completed, or cancelled**. It is preserved through later changes
between those states, so a supervisor review or unresolved review does not
restart or indefinitely extend retention.

Once the clock starts, the mission cannot return to **planned, assigned, or
active**. Later patrol work requires a new assignment with its own mission and
clock. This prevents a privileged edit, retry, or supervisor review from resetting
the original deadline. The Department must complete human review within the
available retention period; an unfinished review does not postpone scheduled
deletion.

The 365-day period is a Department policy choice. It is not a legal safe harbour
and does not, by itself, establish compliance. Before rollout, the Department
must confirm that this period is compatible with police, service, evidence,
records-management, court, audit, and other applicable requirements.

Scheduled deletion may be deferred only when an authorized, documented legal
hold or other law requires continued retention. The hold does not pause or
restart the 365-day clock. A hold must identify its authority, scope, owner,
start date, release condition, and a finite review date no more than 30 days
after placement. Every continued hold must be reviewed again within 30 days;
access remains restricted. The same placement reference remains an idempotency
key after review or release and cannot be reused for different details. Data must
be deleted under the approved process when the hold ends unless another
documented basis applies.

The exact 365-day instant is the point at which evidence becomes eligible for
deletion; it is not a claim that every physical row is removed in the same
millisecond. The server job runs every five minutes and drains at most 2,000
deletable missions per invocation in 100-mission batches. The Department's
operational deletion objective is removal within 15 minutes of eligibility for
an unheld mission without an open-session anomaly. It must alert on a failed or
late job, any remaining deletable backlog after a scheduled drain, or an oldest
backlog age above 15 minutes, and stop new rollout if the capacity objective is
not met.

The purge defers a mission that still has an anomalous open patrol session so it
does not destroy a partial record. The Department must investigate and close that
session; this safeguard is not permission for indefinite retention. An assigned
mission whose duty window has expired by five minutes and which never acquired a
session is automatically cancelled so that it acquires a closure clock. For
post-patrol records that existed before this retention control was installed,
the initial clock uses the later of the scheduled duty end and the record's last
update to avoid deleting evidence early.

Account and membership data may be retained while access remains active and then
handled under the Department's approved identity and records schedule. Security
logs may have a separately approved schedule, but must not silently be used to
retain a second copy of precise patrol routes beyond the applicable period.

On the device, successfully synchronized route points are removed after the
session closes. PatrolGrid stores one server-issued `retention_until` value for a
mission and applies that same clock to every encrypted route point and queued
field action for the mission; individual point or action timestamps are not used
to extend or shorten retention. Unsynchronized evidence becomes due at the same
mission deadline. Cleanup finishes before protected mission content or GPS
capture can resume at app-process start, also runs on a daily schedule without a
network requirement, and runs before synchronization. If Android defers
background work or the device is powered off, overdue local evidence is deleted
on the next successful check.

If local evidence exists but its authoritative server clock has not yet been
learned, PatrolGrid permits a bounded 24-hour close/synchronization recovery
window. After that it fails closed: GPS capture and protected mission evidence
remain unavailable while sign-in and an authenticated recovery/synchronization
path are allowed. PatrolGrid does not guess a deletion deadline from a point,
action, or device-close timestamp. On reconnection it records the server clock
and immediately enforces it. Only the server's explicit PostgreSQL `P0002`
“mission unavailable” response permits one-time local dead-letter cleanup;
ordinary validation errors, HTTP 404 responses, or transient failures do not.
Operational policy must therefore require managed devices with pending evidence
to reconnect at least daily and treat an unrecoverable clock as an incident.

An unreadable queue, a malformed evidence item, or an item with an untrustworthy
timestamp cannot be safely synchronized or retained indefinitely. Before
cross-store deletion, PatrolGrid commits an aggregate deletion-intent journal;
successful cleanup atomically resolves that journal and records only the
aggregate discarded-item count and incident time. A persistent
evidence-integrity warning directs the staff member to the subdivision supervisor
through the official Department channel. The warning can be acknowledged after
reporting, but its aggregate time/count remains on the device. A cleanup failure
continues to block capture until a later cleanup succeeds. Neither record contains
a mission, staff, route, coordinate, or note identifier. An expired or malformed
encrypted mission snapshot is swept by the same retention process; the snapshot
is refused at 24 hours. Uninstalling the app or deleting a local copy does not
delete the Department's server record.

Database backups, point-in-time-recovery logs, snapshots, exports, and replicas
must not become an indefinite second evidence store. Before rollout, the
Department must document and configure an inaccessible disaster-recovery copy
retention maximum of no more than 30 days. A deleted mission may therefore remain
encrypted and unavailable in a recovery copy until that copy ages out, but it
must not return to ordinary use. Every restore must remain network-quarantined;
operators must apply current migrations, reconcile legal-hold releases against
the authoritative hold register, run missed-assignment cancellation and the
retention purge as of the current time until the deletable backlog is zero, and
verify that expired unheld evidence cannot be read before reopening service. An
observed restore drill and the hosted backup/PITR settings are rollout blockers,
not assumptions made by this application repository.

The release configuration and in-app notice only state the selected period. The
Department must operate, monitor, audit, load-test, and restore-test the purge,
backup aging, and legal-hold process. Real-data rollout is not approved until the
live deletion test, capacity/SLO alert test, and quarantined restore test pass.

## 8. Access, correction, export, deletion, and grievances

To ask for a summary or copy of your PatrolGrid data, correction or completion of
inaccurate data, export, deletion, or review of a privacy concern:

1. Contact your subdivision supervisor through the existing official Department
   channel for administrative requests.
2. State that the request concerns PatrolGrid, identify the relevant mission or
   date range where possible, and describe the requested action.
3. The Department may verify identity and authority before disclosing or changing
   a record. It must log the request, route it to an authorized decision-maker,
   and respond through an official channel.

A deletion or correction request does not authorize alteration of an evidentiary
or audit record where retention or preservation is required by law, a valid legal
hold, or an authorized investigation. The Department should explain the decision
and the available escalation path unless law prohibits that disclosure. No
request should be sent through a public GitHub issue or to a personal account.

If a grievance is unresolved, use the Department's existing official grievance
or supervisory escalation process. Any right to approach an external authority
depends on the law in force and any applicable exemption at the time of the
request.

## 9. Security and incidents

The deployment uses encrypted on-device storage for pending coordinates,
authenticated HTTPS network access, role/subdivision restrictions, and audit
records. No technical control eliminates all risk. Staff must use an approved,
screen-locked device, protect credentials, install only the Department-approved
APK, and promptly report a lost device, suspected account misuse, unexpected
tracking, or data exposure through the normal chain of command.

The Department must maintain an incident procedure that can revoke access,
preserve necessary evidence, assess affected people and data, and give required
notifications. The absence of a separate technical-support desk does not remove
that operational responsibility.

## 10. India legal-status note for the deployment owner

The Supreme Court of India recognizes privacy as a fundamental right and applies
legality, legitimate aim, and proportionality to State interference. PatrolGrid's
mission-only collection, purpose limits, human review, and finite retention are
product safeguards; the Department must still identify the law authorizing its
specific deployment and confirm that the collection is necessary and
proportionate.

As of 2 September 2026, the principal notice, processing, fiduciary-obligation,
individual-right, and exemption provisions in sections 3–17 of the Digital
Personal Data Protection Act, 2023 are scheduled to commence 18 months after the
November 2025 Gazette publication and are not yet in force. The corresponding
Rules 3 and 5–16 have the same phased commencement. The Department must check for
new notifications before every release and again before rollout.

The enacted Act includes certain legitimate uses for State functions and
employment. It also contains targeted exemptions for offence prevention,
detection, investigation, or prosecution, and allows the Central Government to
notify particular State instrumentalities for specified interests. These are not
a basis to assume that every police or patrol record is exempt. Applicability must
be decided record-by-record and purpose-by-purpose by the Department's authorized
legal authority. Even where an exemption applies, the Department should preserve
the transparent and protective practices in this policy unless law requires a
restriction.

The forthcoming framework also contemplates a published business contact, an
effective grievance route, access/correction/erasure mechanisms, and defined
response period that, under the notified Rules, may not exceed 90 days. “No
technical-support desk” therefore cannot mean “no reachable privacy contact”;
this draft assigns that role to the subdivision supervisor via the existing
official Department channel. The exact channel and response period remain
deployment fields that the Department must publish. The 365-day PatrolGrid
policy must not be confused with any statutory log-preservation, police-record,
evidence, or legal-hold requirement.

The current Android notice is in English. The enacted Act provides for access to
certain notices and consent requests in English or an Eighth Schedule language.
The Department must decide which approved translations its staff deployment
requires and complete them before the relevant provisions commence, or earlier
if another applicable employment or Department rule requires them.

Authoritative references reviewed for this draft:

- [Digital Personal Data Protection Act, 2023 — official Gazette copy](https://www.meity.gov.in/static/uploads/2024/02/Digital-Personal-Data-Protection-Act-2023.pdf)
- [G.S.R. 843(E), 13 November 2025 — phased commencement](https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf)
- [Digital Personal Data Protection Rules, 2025 — official Gazette copy](https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf)
- [Justice K.S. Puttaswamy (Retd.) v. Union of India, 24 August 2017](https://api.sci.gov.in/supremecourt/2012/35071/35071_2012_Judgement_24-Aug-2017.pdf)

## 11. Changes to this policy

Material changes to collection, purpose, recipients, provider handling,
retention, request routes, or staff safeguards require a new notice version and
Department approval. Version 3 adds the 365-day mission-closure rule, the
subdivision-supervisor privacy/grievance route, the absence of a separate
technical-support desk, the operational escalation boundary, and the named
public-map-provider disclosure. Staff must be shown the new notice before
protected app content is available.
