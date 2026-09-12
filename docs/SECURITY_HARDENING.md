# Security hardening and infrastructure decisions

Last reviewed: 2026-09-12

DailyBeat is an offline-first Android app. Location history and diary content stay on the phone;
the only managed backend surface is opt-in Supabase cloud backup. Security work must preserve that
small attack surface rather than adding servers solely because a tool appears on a generic
"enterprise" checklist.

## Implemented controls

- GitHub Actions now runs Gitleaks, TruffleHog, Semgrep OSS, OpenGrep, Trivy, dependency review,
  and CodeQL. Every referenced action is pinned to an immutable commit SHA, and every downloaded
  standalone scanner binary is version pinned and SHA-256 verified before use.
- Release publication waits for both emulator lanes, the live backup/RLS lane, CodeQL, and the new
  `oss-security` gate. A security scan cannot be bypassed by publishing faster than CI finishes.
- Dependabot covers Gradle, GitHub Actions, and Python with a seven-day cooldown. Gradle artifacts
  are additionally checksum pinned in `android/gradle/verification-metadata.xml`.
- Cloud backup accepts only a canonical HTTPS origin, or an explicit loopback HTTP origin for local
  development. URL user-info, paths, query strings, fragments, control characters, and oversized
  anonymous keys are rejected before a request can be built.
- Export filenames cannot escape DailyBeat's app-owned directory. Temporary PDFs, archives, and
  diagnostic logs are deleted, or truncated if the filesystem refuses deletion.
- The local Supabase model enforces 12-character complex passwords and recent authentication for
  password changes. Remote production settings must be kept at least as strict in the Supabase
  dashboard.
- PostgreSQL row-level security restricts every backup row to `auth.uid()`, anonymous access is
  revoked, and JSON backup size is capped at 12 MiB. The pgTAP adversarial suite exercises
  cross-user reads, writes, deletes, owner forgery, and the size limit.
- Android refuses remote cleartext traffic and cross-host redirects, disables Android backups, uses
  encrypted storage for sessions/API keys, and exports only the required launcher activity. A unit
  test guards that exported-component exception.

## 2026-09-12 audit evidence

| Tool | Result | Disposition |
|---|---|---|
| Gitleaks 8.30.1 | 0 leaks across 255 commits | CI gate added |
| TruffleHog 3.97.4 | 0 verified secrets; one historical fake adversarial URI | Verified-secret CI gate added |
| Trivy 0.74.0 | 0 vulnerabilities, secrets, or misconfigurations | High/critical CI gate added |
| Semgrep OSS 1.177.0 | Dependabot cooldown gap fixed; launcher export confirmed as required | CI gate added with the one documented launcher exception |
| OpenGrep 1.30.0 | Same actionable results as Semgrep | Independent CI gate added |
| SonarQube Community | Quality gate passed; parser ReDoS and ignored cleanup results fixed | Manual release audit; no hosted token required |
| OWASP Dependency-Check 13.0.0 | NVD rejected anonymous updates | Re-run when `NVD_API_KEY` is provisioned; Trivy covers current CVEs meanwhile |
| Supabase pgTAP | 15/15 RLS and payload-limit tests passed | Required backend workflow |
| OWASP ZAP | Bounded scan reached the local authenticated API but the stable container did not complete | Manual pre-release scan; never point active scan at production |
| SQLmap 1.10.9 | Bounded high-level scan found no confirmed injectable parameter | Manual pre-release scan against disposable local Supabase only |

Raw local scanner output is intentionally ignored by Git because it can contain repository paths,
test fixtures, or ephemeral local credentials. CI publishes no secret-bearing scanner artifact.

## Added implementation backlog

The requested repositories are tracked here with an explicit fit decision. "Conditional" means the
control becomes appropriate only if DailyBeat adds a self-hosted service; it is not unfinished work
for the current phone + managed-Supabase architecture.

| Repository / control | Status | DailyBeat decision |
|---|---|---|
| Ory Kratos + Keto | Conditional | Keep Supabase Auth plus PostgreSQL RLS now. Re-evaluate Ory only if identity/authorization moves to a self-hosted multi-tenant backend. Running two identity planes would increase account-takeover and recovery risk. |
| Infisical | Conditional | Runtime Cloud AI keys remain encrypted on-device; CI signing and backend values remain GitHub/Supabase secrets. Adopt Infisical when a team-managed staging/production secret inventory exists and access credentials can be provisioned. Never bundle an Infisical machine credential in the APK. |
| OpenTelemetry Collector + Uptrace | Conditional | Keep privacy-redacted, bounded local operational logs. Add backend-only telemetry if a DailyBeat service is introduced, excluding coordinates, place names, diary text, prompts, tokens, and user identifiers. |
| Fail2ban | Not applicable on device | Supabase edge/Auth rate limits protect the managed endpoint. Use Fail2ban only on a future self-hosted Linux ingress; it cannot protect an Android APK. |
| slugify / validator.js | Implemented natively | Kotlin validates URL, credentials, filenames, sizes, and report inputs; PostgreSQL adds schema constraints and RLS. Adding JavaScript validation to a Kotlin app would not create a server-side trust boundary. |
| Temporal | Conditional | Android WorkManager provides bounded, persistent device jobs; backup uses owner-bound idempotent upsert. Adopt Temporal only for future multi-step server workflows that must survive server restarts. |
| nektos/act | Implemented for local validation | Use `act -l` and `act --dryrun` to validate workflow structure. Android emulator and GitHub-hosted security features still require real CI. |
| Renovate | Covered by Dependabot | Do not run two competing dependency bots. Dependabot now has cooldowns, grouping, review gates, and Gradle checksum verification. Re-evaluate Renovate only if cross-file version orchestration outgrows Dependabot. |
| Caddy | Conditional | The APK requires HTTPS and Supabase supplies managed TLS/edge isolation. Put Caddy in front of a future self-hosted API, never inside the phone app. |

## Remaining operator actions

- Keep production Supabase password and rate-limit settings aligned with `supabase/config.toml`;
  local configuration does not automatically mutate a hosted project.
- Provision an NVD API key only if OWASP Dependency-Check is retained as a second SCA source.
- Re-run ZAP and SQLmap only against an isolated local/staging project with synthetic data.
- Review GitHub security alerts and dependency-update pull requests weekly; scanners reduce risk but
  do not replace code review or key rotation after a confirmed leak.
