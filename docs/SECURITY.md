# PatrolGrid Security Baseline

PatrolGrid handles sensitive operational location data. Its security baseline follows the control areas in the [OWASP Mobile Application Security Verification Standard](https://mas.owasp.org/MASVS/02-Frontispiece/), Android's platform security guidance, and the mobile-device principles in the [FBI CJIS Security Policy](https://le.fbi.gov/file-repository/cjis_security_policy_v6-0_20241227.pdf).

This document distinguishes controls implemented in the Android client from controls that require a department backend or deployment process. The current app is a protected pilot build, not a claim of CJIS certification.

## Implemented in the Android client

### Mission-bounded collection

- Location collection is off by default.
- The foreground location service starts only for an explicit active mission and stops when the mission ends.
- The persistent Android notification makes active tracking visible.
- The app does not request background-location permission.
- The PatrolGrid manifest does not request call-log, microphone, or shared-storage access.
- Patrol review asks for context when evidence is incomplete; it does not create employee rankings or automatic misconduct findings.

### Route evidence at rest

- Latitude, longitude, and accuracy are serialized and encrypted with AES-256-GCM before Room receives them.
- The AES key is non-exportable and generated in Android Keystore. StrongBox is used when the device supports it, with Android Keystore as the safe fallback.
- Mission id and timestamp are authenticated as AES-GCM additional data. Moving ciphertext to a different mission or timestamp fails authentication.
- Database migration 5-to-6 encrypts existing pilot route points before removing the old plaintext coordinate columns.
- Keystore failure is fail-closed: no plaintext location fallback is written.
- Android cloud backup and device-to-device transfer are disabled for all app data.

This follows Android's recommendations to use [Android Keystore](https://developer.android.com/privacy-and-security/keystore) and [AES/GCM/NoPadding](https://developer.android.com/privacy-and-security/cryptography).

### Network and display protections

- Android Network Security Configuration rejects cleartext traffic and trusts system certificate authorities only.
- Sensitive release screens use `FLAG_SECURE` to block screenshots and non-secure displays.
- Android 12 and later hide third-party overlay windows to reduce tapjacking risk.
- Components that do not need external access are `exported=false`; the launcher activity is the only exported component.
- Release builds use R8 code and resource shrinking. Release artifacts are signed and their expected certificate is checked by CI.

### Release-access boundary

- The local supervisor/patrol role switch exists only in debug builds.
- Release builds do not allow role switching from the More screen.
- Local pilot onboarding is not an identity provider. It must be replaced with server-provisioned roles before operational deployment.

## Required before operational deployment

These protections cannot be safely simulated in an offline Android prototype:

1. **Department identity:** OIDC Authorization Code + PKCE or managed passkeys, phishing-resistant MFA for supervisors, short-lived access tokens, and biometric/device-credential reauthentication for sensitive actions.
2. **Server-enforced authorization:** subdivision/tenant isolation and role-based access checks on every mission, route, and evidence API. Never trust a role supplied by the Android client.
3. **Integrity verification:** Play Integrity verdicts verified by the backend, with risk-based responses rather than client-only blocking. For managed fleets, evaluate hardware-backed key attestation and verify certificate chains on a trusted server.
4. **Protected synchronization:** TLS-only APIs, replay-resistant request signing where justified, idempotent offline sync, conflict handling, and encrypted server storage with managed key rotation.
5. **Tamper-evident audit:** append-only records for assignment, patrol start/end, evidence edits, supervisor review, exports, and administrative access. Keep officer explanations with the evidence.
6. **Device lifecycle:** mobile-device management, screen-lock policy, patch requirements, remote session revocation, lost-device response, and documented incident escalation.
7. **Data governance:** approved retention periods, legal holds, export rules, deletion procedures, purpose limitation, access review, and jurisdiction-specific privacy/legal approval.
8. **Independent assurance:** OWASP MASVS-based mobile assessment, API penetration test, threat-model review, dependency scanning, and a formal mobile-app vetting process such as [NIST SP 800-163 Rev. 1](https://csrc.nist.gov/pubs/sp/800/163/r1/final).

Certificate pinning is intentionally not enabled before the production API hostname and certificate-rotation process exist. A hard-coded pilot pin can turn routine certificate renewal into a fleet-wide outage. Android's [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config) can add managed pins with backup keys once those operational details are known.

## Security verification in this repository

- JVM tests validate the route-point codec and invalid-input rejection.
- Android instrumentation tests verify Keystore encryption/decryption, authenticated mission binding, and the absence of plaintext coordinate columns.
- CI runs unit tests, Android lint, a debug build, and Android 14 instrumentation tests.
- Security-sensitive changes should be reviewed against OWASP MASVS storage, crypto, authentication, network, platform, code-quality, and resilience control groups.

## Reporting a vulnerability

Do not place patrol data, credentials, exact locations, or exploit details in a public issue. Contact the repository owner privately and include only the minimum information needed to reproduce the problem in a non-operational test environment.
