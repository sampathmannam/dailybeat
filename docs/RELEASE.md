# PatrolGrid private APK release

PatrolGrid is distributed directly to authorized staff, not through Google Play.
GitHub builds an unsigned, minified candidate and encrypts it only to the committed
PatrolGrid public key. The permanent APK key, both key passwords, and the OpenPGP
decryption key remain on the designated offline-signing Mac. There is no symmetric
transfer secret and GitHub never publishes a plaintext APK or mapping.

**No production tag or release has been created. The app is not rollout-ready.**
The current source deliberately has an `UNCONFIGURED` production backend and
`UNAPPROVED` privacy policy. The production environment has no secrets; the two
required mobile-client values are intentionally absent. FileVault is also currently
off on the designated Mac. Every one of those conditions fails closed.

## Fixed identities

- Package: `com.dailybeat.app.patrolgrid`
- Version/tag scheme: `MAJOR.MINOR.PATCH` / `patrolgrid-vMAJOR.MINOR.PATCH`
- APK certificate SHA-256:
  `1b1351160170796ec9818047e790a5474c8544ad867f62736e8d93fe2a8c025b`
- OpenPGP primary: `AA2B9126F5750A6690CEA90410B087D428F60413`
- Exact encryption subkey: `84AD08D70EC95222457C16CEFEFD926C2C74FB9E!`
- Fixed Android SDK Build Tools: `36.0.0`

The public certificate and OpenPGP key are committed under `release/`. Two tested,
encrypted offline copies of the APK private key, OpenPGP private key, trusted-tool
bundle, and recovery instructions are mandatory before first rollout.

## Production configuration

`android/patrolgrid-production.properties` source-pins the exact production HTTPS
origin, SHA-256 of the mobile anon key, privacy approval status, and notice version.
A release requires a reviewed origin/hash, `PRIVACY_POLICY_STATUS=APPROVED`, and
`PRIVACY_NOTICE_VERSION=3`. Environment/Gradle backend-identity and privacy-status
overrides are forbidden. The workflow supplies only the exact 40-hex
`PATROLGRID_RELEASE_COMMIT` plus the two environment values:

- `PATROLGRID_PRODUCTION_SUPABASE_URL`
- `PATROLGRID_PRODUCTION_SUPABASE_ANON_KEY`

The URL and anon key are mobile-client configuration, not administrator credentials.
Their values must match the committed origin/hash. There must be no
`PATROLGRID_RELEASE_*`, `DAILYBEAT_*`, keystore, password, or transfer-key secret in
GitHub.

## Repository governance

The owner-authenticated Mac preflight binds all live governance details, including:

- the exact `sampathmannam` GitHub user id and current repository-admin authority;
- exact active rulesets `22066728`, `22066729`, and `22066730`, with no main bypass;
- squash-only merging and a signed current-main commit;
- required `build`, `patrolgrid-backend`, `dependency-review`, and `codeql` checks;
- exact Actions allowlist/SHA-pin policy and production-environment reviewer/tag policy;
- enabled vulnerability alerts, Dependabot security updates, secret scanning, and secret
  scanning push protection (all fail closed if the owner session cannot read them);
- a GitHub-verified annotated tag whose OpenPGP signature cryptographically validates
  under the exact committed primary key; and
- tag commit exactly equal to current protected `main` HEAD.

Validate the first signed squash PR before relying on the new signed-main rule. An old
ancestor cannot be newly released. A rollback uses reviewed old source in a new commit
with a higher version code.

## What GitHub may publish

The release workflow has three jobs. Hash-locked Python policy tests run in a separate
job and pass no artifact to the builder. The artifact job starts from a fresh checkout,
checks tracked and untracked source state, tests/lints/builds with strict Gradle locks
and verification, and verifies the full merged-manifest security policy. SBOM scanning
uses an isolated APK copy; the original APK, mapping, manifest, identity, and source
state are rechecked after the final third-party action.

The candidate archive contains exactly the unsigned APK, R8 mapping, SPDX SBOM, and a
JSON manifest binding package/version/code, full commit, backend identity/hashes,
privacy approval, workflow ref/run, merged-manifest hash, file sizes, and file hashes.
It is encrypted to the exact public encryption subkey. Only that ciphertext crosses
jobs. The no-checkout publisher downloads the exact Actions artifact by id, normalizes
the plain upload-artifact SHA-256 output against GitHub REST's `sha256:` representation,
verifies the Actions ZIP digest separately from the ciphertext digest, safely extracts its sole
bounded member, and uploads only that ciphertext to an authenticated draft.

Never publish the draft. The one-day Actions artifact must still exist when the Mac
ceremony runs because the Mac independently downloads it and byte-compares its sole
ciphertext with the draft asset.

## One-time trusted-tool bootstrap

Do not run release/signing helpers from a mutable checkout. After independently
reviewing the exact current-main commit and bootstrap, install the root-owned tool
bundle once:

```bash
./scripts/mac_bootstrap_patrolgrid_release_tools.sh install <reviewed-40-hex-main-commit>
```

This command does not create a tag or release. It stages files from the reviewed Git
object, confirms the working copy and GitHub main match it, then asks for `sudo` only
to install the immutable bundle at:

```text
/Library/Application Support/PatrolGrid/release-tools/current
```

No automated bundle replacement is shipped. Any change to its launcher, helper,
installer, ADB common code, manifest verifier, public key, certificate, or pinned
toolchain blocks rollout until a new independent audit and recorded admin reinstall.
The root-installed launcher refuses a signed tag whose security-sensitive release
files differ from that separately audited bundle.

This launcher protects against accidental dirty-checkout execution; it is not a
boundary against malware running as `sujithsampath`. That account can read the P12/GPG
files and request Keychain values. High-assurance rollout therefore requires a
dedicated offline signing account/device and hardware-backed keys or Keychain access
control requiring user presence on every signing/decryption operation. Treat the
designated account as fully trusted until then.

Enable FileVault before preflight. Temporary cleanup is logical deletion, not forensic
secure erase on APFS/SSD.

## Exact local ceremony

All commands use the absolute trusted launcher:

```bash
'/Library/Application Support/PatrolGrid/release-tools/current/bin/patrolgrid-release' check
```

After the reviewed version commit is exactly current `main`, create the tag with the
fixed OpenPGP homedir/key helper—never plain `git tag -s`:

```bash
'/Library/Application Support/PatrolGrid/release-tools/current/bin/patrolgrid-release' \
  create-tag patrolgrid-v1.0.0
git push origin refs/tags/patrolgrid-v1.0.0
```

Wait for `Release PatrolGrid APK` to succeed and leave its ciphertext-only draft. Then
request one new owner-only bundle path. Its existing parent must be owned by the signing
account, have no extended ACL, and not be group/world-writable:

```bash
'/Library/Application Support/PatrolGrid/release-tools/current/bin/patrolgrid-release' \
  ceremony patrolgrid-v1.0.0 \
  ./owner-vault/PatrolGrid-1.0.0-owner-bundle
```

The launcher fetches the signed tag into a fresh detached checkout. The ceremony then
rechecks governance, exact tag signer, successful workflow, workflow ref, artifact ZIP
and ciphertext byte identity, bounded archive allowlist, hashes, commit-pinned backend
and privacy sources, package/version/code, min/target SDK, permissions, exported
components, backup/cleartext/network policy, and non-debuggable state. It embeds
APK-signature-covered release metadata binding the tag, full commit, workflow,
Actions-artifact id/archive digest, draft ids, ciphertext, unsigned APK, manifest,
mapping, and SBOM hashes. It zipaligns, signs offline, and repeats every binary check.

The ceremony exposes the entire mode-0700 bundle with one same-parent, no-clobber
atomic rename. Its `staff/` child contains only the canonical APK, its two-entry
checksum file, and the SBOM; its separate `owner/` child contains only the mapping.
Send only `staff/` to staff. Never copy or share the bundle root or `owner/` child.
If the final parent-directory durability sync cannot be confirmed, the complete bundle
is left visibly quarantined and the ceremony tells you not to distribute it. A process
crash cannot publish just one child because neither child is visible at the destination
until the single bundle rename succeeds.

## Verified phone installation

Use the trusted launcher, not the repo installer:

```bash
'/Library/Application Support/PatrolGrid/release-tools/current/bin/patrolgrid-release' \
  install ./owner-vault/PatrolGrid-1.0.0-owner-bundle/staff <phone-adb-serial>
```

The installer bounds/snapshots all three files, repeats certificate, metadata, merged
manifest, package/version/code, checksum, and SBOM checks, launches a pinned adb server
on a fresh local port, binds the selected device model/build fingerprint, installs,
pulls the installed `base.apk`, and requires byte identity before launch. Runtime
permissions are never auto-granted.

For the **first install**, use this verified ADB/managed-device process. Sending an APK
and checksum through the same channel is not authentication because an attacker can
replace both. If hands-on installation is impossible, publish the APK SHA-256 and
certificate fingerprint through a separate authenticated Department channel and use a
trusted verification procedure. Later Android updates additionally enforce the same
signing key.

No independent reproducible/local rebuild is implemented yet; the Mac signs the
provenance-bound hosted-runner binary. Hosted-runner source-to-binary fidelity remains
a production rollout blocker until an independently clean reproducible build is
compared or the risk is formally accepted after security review.

Operational emergencies remain on the normal command/radio/phone chain. Privacy,
correction, deletion, and grievance requests use the subdivision supervisor's official
Department channel; this project does not create a technical support desk.
