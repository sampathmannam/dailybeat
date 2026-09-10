# DailyBeat — v3.8.1 baseline record

This is the frozen reference point for DailyBeat. Version **3.8.1** (`versionCode 17`, tag
`v3.8.1`) is the shipped release: map-first Today, reviewable whole-day Beats, private 28-day
Insights, reliable GPS breadcrumbs with capture-gap disclosure, a one-hour privacy pause, cloud
backup, and a persistent System/Light/Dark appearance selector.

It is a baseline, not a claim of perfection. Everything below is what was actually verified, plus
an honest list of what still is not.

## What the release gate proves

A signed APK is published only after every one of these passes on a protected `main` commit whose
version matches `release/version.txt`:

| Gate | Scope |
|---|---|
| `build` | `assembleDebug`, 194 JVM unit tests, Android Lint |
| `instrumentation` | 31 Compose/Android tests on an API 34 emulator, with screenshot and logcat evidence |
| `offline-instrumentation` | the same suite with the live-backup test excluded, so an outage cannot mask an app defect |
| `live-backup` | a real Supabase upload → download → restore round trip against a dedicated QA account |
| `release-policy` | 31 Python tests pinning build, signing, QA-isolation, supply-chain and backup policy |
| `codeql` | static analysis of Kotlin/Java and the Python release tooling |
| `dependency-review` | fails a pull request on a high-severity advisory |

The publisher then verifies the permanent signing certificate fingerprint before it will upload
anything. Data survives updates through additive Room migrations only; `MigrationChainTest` fails
the build if a schema version is raised without a migration, and `DatabasePolicyTest` keeps
`fallbackToDestructiveMigration` out of the codebase.

## Verified security posture

Confirmed by source audit at `v3.8.1`:

- **Keys.** The provider API key is held in `EncryptedSharedPreferences` under an AES256-GCM master
  key with no plaintext fallback. It is never logged, never placed in an exception message or URL,
  and is excluded from the cloud backup snapshot. Supabase session tokens are stored the same way.
- **Logging.** `src/main` contains no `Log`, `println` or `printStackTrace` call at all. The
  operational failure log sanitises bearer tokens, API keys, prompts and coordinate pairs before
  writing, and is bounded and kept in internal storage. R8 now strips `android.util.Log` from
  release builds so a future call cannot survive into a signed APK.
- **Attack surface.** Exactly one exported component — the launcher activity, which reads no Intent
  extras. Every receiver, the location service and the FileProvider are unexported. The permission
  list is minimal and each entry is used; background location is requested incrementally and
  capture deliberately does not require it.
- **Backups.** `allowBackup="false"`, `fullBackupContent="false"` and explicit data-extraction rules
  excluding both cloud backup and device transfer.
- **Network.** Every endpoint is HTTPS, cleartext is denied by an explicit network security config
  (loopback excepted, for a locally hosted model server), user-supplied base URLs are HTTPS-enforced,
  redirects are refused so custom auth headers cannot be replayed, every client has bounded
  timeouts, and every response is size-capped before parsing.
- **Cloud payload.** User text is sanitised so it cannot forge citation tokens, the system prompt
  treats supplied data as untrusted, output is integrity-validated against surviving references, and
  both context and token budgets are bounded.
- **Capture integrity.** Mock locations are rejected; so are non-finite, out-of-range, stale,
  inaccurate and physically impossible fixes. Rejections are counted, not silently dropped.
- **Backend.** Row-level security is enabled on the backup table with four separate owner-only
  policies and `with check` on both insert and update; the trigger runs `security invoker` with an
  empty `search_path`.
- **Supply chain.** Gradle dependency verification pins every artifact by SHA-256, enforced by a
  policy test that fails if signature verification or any trusted-key bypass is introduced. All
  GitHub Actions are SHA-pinned and the repository requires SHA pinning. Workflows use
  least-privilege tokens and never `pull_request_target`. Signed commits are required on `main`, and
  release tags are immutable. Secret scanning with push protection and Dependabot are enabled, with
  no open alerts of any kind.
- **QA isolation.** Debug builds are forced to a `.qa` package by a `require()` guard, and
  destructive instrumentation can only target the disposable `.qa.e2eloop` package, so neither
  production nor regular QA data can be cleared by a test run.

## What this baseline does not establish

1. A multi-day physical-device trial measuring missed visits, false visits, GPS error and battery
   drain with battery optimisation both enabled and exempted. OEM task killers remain the largest
   unmeasured risk to passive capture.
2. A formal end-to-end privacy review of exactly what leaves the device during diary generation and
   cloud backup, and of how the place-level and stop-level privacy controls are enforced along every
   outbound path. **This is the most important open item and should be closed before the app is
   used with genuinely sensitive locations.**
3. Encryption at rest. The Room database is plaintext, protected only by Android file-based
   encryption and the disabled-backup settings. Exports are deliberately readable files and still
   need a retention policy.
4. Confidentiality of cloud backup from the backend operator. Row-level security is authorisation,
   not encryption; the snapshot is stored as plaintext JSON.
5. Real-provider behaviour — latency, cost, rate limits, outages, malformed responses — measured
   against restricted test keys.
6. A restore verified onto a second physical device from the same non-production account.
7. Automated accessibility checks and screenshot comparison across small, large-font and landscape
   configurations.
8. Certificate pinning for the provider, geocoder and backup hosts.
9. Privacy-preserving crash and ANR monitoring, with documented support and rollback ownership.
10. A staged rollout. Every release currently reaches every device at once through Obtainium.

## Scope note

DSR is a separate application in the private `sampathmannam/dsr` repository. DailyBeat retains the
dormant DSR tables and migration chain purely so existing records survive upgrades — see
[DSR_COMMAND.md](DSR_COMMAND.md). There is no DSR screen, route or service in this app.

PatrolGrid is a separate product that was never merged into this repository.
