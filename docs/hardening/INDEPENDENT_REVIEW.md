# Independent security review packet

## Scope and evidence

Target the published v4.2.0 Android app (`com.dailybeat.app`, code 32) at source commit
`b62ba9e9914fbbed337ef21ca59869b335007a5e`, plus the operational hardening changes described in
[the follow-up report](2026-09-19-operations.md). Preserve the existing production signing identity.
The [release report](../V4_2_0_RELEASE_REPORT.md) links the validation scope; GitHub's release assets
and exact-commit workflow runs establish artifact provenance. Record the exact review commit and
APK checksum in the review, since main may move afterward.

Automated scanners and the implementation team's tests are useful evidence, not an independent
review. A separate reviewer must accept this scope and provide findings, severity, reproduction,
affected version, remediation and retest results. No reviewer has been appointed in this work.

## Review boundaries

Use synthetic data in a separately identified QA package and isolated local/staging Supabase.
Do not actively scan production, extract personal diaries, collect real coordinates, or erase any
existing user account. The bounded production deletion script is a distinct, explicitly operated
acceptance drill. Agree on any other production testing separately. Keep the report private until
sensitive findings are resolved; the repository owner chooses disclosure and recipient access.

## Required attack and failure cases

| Area | Required challenge | Source and evidence entry point |
|---|---|---|
| Cloud authorization | Cross-user read/write/delete, owner forgery, anonymous calls, direct-table versus RPC permissions, concurrent quotas and immutable published archives | `supabase/migrations`, `supabase/tests`, `scripts/archive_backend_smoke.py` |
| Destructive authentication | Forged/missing token, refreshed old password authentication, wrong body user ID, Auth outage, partial deletion, fresh caller and cascade verification | `supabase/functions/delete-account`, scoped deletion drill |
| Backup confidentiality/integrity | Ciphertext tampering, wrong passphrase, truncation, reordered/missing pages, size/resource limits, rollback on invalid restore and legacy compatibility | Android backup implementation, native archive-recovery tests |
| Device data and lifecycle | App sandbox/exported components, key/session storage, Android backup exclusion, process death, full disk, durable capture replay, restore/erase races, upgrade paths | Android manifest, capture/data/backup packages, Room migrations and instrumentation |
| Privacy and network | Overlapping private zones, hidden stops, address lookup opt-in and final dispatch checks, redirect/cleartext rejection, support preview and export sanitization | Android privacy/geocoding/report/export tests and support UI |
| Release and supply chain | Permanent certificate, exact-commit required checks, immutable assets, dependency checksums, pinned workflows, privileged credential exposure | `.github/workflows`, release policy tests, Gradle verification metadata |
| Operations | Live schema versus ledger drift, backup restore including Auth, erasure reconciliation after snapshot recovery, incident responsibility and scoped secret handling | Schema baseline, operations runbooks, recovery and deletion reports |

## Acceptance

Close all reproducible critical/high findings with regression tests and independent retesting.
For lower-severity residual risks, record the concrete consequence, owner and due date or explicit
risk acceptance. “No findings” requires a report stating what was actually exercised, environments,
limitations and date; a green scanner dashboard is insufficient. Performance, battery life and
capture recall need the separate physical field trial and must not be inferred from security tests.
