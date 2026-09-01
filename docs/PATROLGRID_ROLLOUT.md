# PatrolGrid production rollout

PatrolGrid should be released as a managed operational tool, not as an employee-scoring or continuous-surveillance product. Route evidence supports supervisor review; it must never make an automatic misconduct finding.

## Release gates

All gates are required before subdivision-wide deployment.

- Link the repository to a dedicated hosted Supabase production project and apply every migration from a clean staging restore first.
- Keep public sign-up and anonymous sign-in disabled. Provision staff through an approved administrator workflow and immediately disable access when a staff member transfers or leaves.
- Configure the Android release build with only the Supabase URL and anonymous key. The service-role key must exist only in protected backend/CI secret storage and must never be embedded in the APK.
- Require unique staff accounts, 12+ character passwords, breached-password protection where available, short access-token expiry, refresh-token rotation, and administrator-assisted account recovery. Require MFA or organizational SSO before handling highly sensitive operational data.
- Verify Row Level Security with real staging accounts for a supervisor, two patrol staff in the same subdivision, a different subdivision, a disabled account, and an account with no membership.
- Enable database backups and point-in-time recovery; perform and time a restore drill. Define route-evidence retention and deletion periods with the department's legal/data-protection authority.
- Distribute through a managed Play track or MDM. Use Play App Signing, protect the signing account with hardware-backed MFA, and permit only supported, screen-locked, encrypted devices.
- Add server-verified Play Integrity verdicts before enforcing device trust. Do not rely on an app-only integrity check; the backend must verify the token and apply a documented fail/degrade policy.
- Configure monitoring for authentication spikes, repeated RLS denials, sync failures, unusual evidence volume, disabled-account activity, database capacity, backup failure, and elevated API latency. Never put coordinates, tokens, passwords, or staff notes in analytics/crash logs.
- Run the full automated suite, a poor-network/offline field exercise, a device-reboot recovery exercise, a permission-denial recovery exercise, and a supervised day/night patrol pilot before widening access.

## Staged deployment

1. **Staging:** synthetic data only; execute schema reset, pgTAP, real Auth/PostgREST E2E, Locust, Android unit/lint, emulator instrumentation, and release-build checks.
2. **Pilot:** one supervisor and a small voluntary patrol group for day and night missions. Confirm tracking stops at End patrol, pending evidence eventually syncs, and supervisor views are understandable without a compliance score.
3. **Limited rollout:** one subdivision, daily operational review, rapid account revocation, and an on-call incident contact.
4. **Full rollout:** expand only after the pilot acceptance criteria and restore drill pass. Review permissions, retention, incidents, accessibility, and staff feedback every release.

## Pilot acceptance criteria

- No route capture before Start patrol or after End patrol.
- No loss or duplication of route points, visits, observations, deviations, or session close across airplane mode, process death, and device reboot.
- A patrol user cannot read peer evidence; a supervisor cannot read another subdivision; a disabled member loses access immediately.
- P95 API latency remains within the operational target under expected concurrent load, with zero authorization failures for valid workflows and zero successful cross-boundary requests.
- Staff can recover from denied permissions, expired sessions, offline startup, and interrupted sync without reinstalling the app.
- Supervisors review exceptions with human context; the system exposes no rank, productivity score, or automatic disciplinary label.

## Incident response

If a device or account is lost or suspected compromised: disable the membership and Auth user, revoke sessions, remove the device from MDM/managed Play, preserve relevant audit events, rotate exposed credentials, and document the decision to restore or invalidate pending evidence. If a service-role credential is exposed, rotate it immediately and review every privileged event during the exposure window.
