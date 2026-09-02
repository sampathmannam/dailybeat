# PatrolGrid Mac development and direct installation

Use an Android Studio emulator for routine QA. Use a USB phone for GPS/device checks,
always with its explicit adb serial. Debug/QA uses
`com.dailybeat.app.patrolgrid.qa`; production uses
`com.dailybeat.app.patrolgrid`.

## QA development

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices
./scripts/mac_emulator_demo.sh
./scripts/mac_sync_and_run.sh <emulator-or-phone-serial>
```

Runtime permissions remain ungranted by default so consent is tested. Synthetic QA may
explicitly pre-grant them:

```bash
PATROLGRID_GRANT_QA_PERMISSIONS=1 ./scripts/mac_sync_and_run.sh emulator-5554
```

The QA helper never switches branches or pulls implicitly. A deliberate clean,
fast-forward-only sync is opt-in:

```bash
PATROLGRID_SYNC_REMOTE=1 ./scripts/mac_sync_and_run.sh emulator-5554
```

## Approved production release

GitHub contains only a public-key-encrypted unsigned candidate. Never download or
install a plaintext APK from a GitHub release and never invoke repo release helpers
directly. After the separately audited one-time bootstrap described in `RELEASE.md`,
use only:

```bash
TRUSTED='/Library/Application Support/PatrolGrid/release-tools/current/bin/patrolgrid-release'
"$TRUSTED" check
"$TRUSTED" create-tag patrolgrid-v1.0.0
git push origin refs/tags/patrolgrid-v1.0.0
"$TRUSTED" ceremony patrolgrid-v1.0.0 \
  ./owner-vault/PatrolGrid-1.0.0-owner-bundle
"$TRUSTED" install ./owner-vault/PatrolGrid-1.0.0-owner-bundle/staff <phone-adb-serial>
```

The ceremony publishes one mode-0700 owner bundle atomically. Share only its three-file
`staff/` child; its separate `owner/` child contains the mapping and must remain private.
The installer accepts only the untouched staff directory produced locally by the
ceremony. It does not accept a URL, draft, ciphertext, or raw APK. A first
production install must use the verified ADB/managed-device flow or a separately
authenticated out-of-band hash and certificate-verification procedure.

## Expected fail-closed messages today

- FileVault is off: enable it before handling plaintext release material.
- The GitHub CLI session is not the exact repository owner with current admin authority:
  correct that owner-authenticated session before a release ceremony.
- Backend/privacy source is `UNCONFIGURED` / `UNAPPROVED`: complete Department and
  production-service review; do not add a placeholder bypass.
- Production environment values are absent: add only the exact reviewed Supabase URL
  and anon key after the committed hashes match.
- Root trusted bundle absent: independently audit and run the one-time bootstrap; do
  not copy or execute a mutable checkout helper as a workaround.

For emulator/Java issues, start Android Studio's emulator and use its bundled JBR.
Install Build Tools 36.0.0 from SDK Manager. Do not restart or replace pinned release
tools during a ceremony. No automated upgrade is shipped; a changed tool or bundle
blocks release until a new independent audit and recorded admin reinstall.
