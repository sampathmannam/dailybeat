# DailyBeat 4.2.1 release evidence

Version 4.2.1 (code 33) is a focused navigation update on top of the capture, privacy and recovery
hardening in 4.2.0. The production package remains `com.dailybeat.app`, the permanent signing
identity is unchanged, and installation through GitHub Releases or Obtainium upgrades the existing
app without deleting local history.

## Changes

- Days retains the direct **Go to date** picker and removes the redundant Older days / Newer days
  paging controls.
- Settings replaces one continuous page with four production categories: Capture & places,
  Privacy & data, Journal & appearance, and Backup & Cloud AI.
- Every existing Settings control remains available in its corresponding category.
- The Settings detail header remains pinned while content scrolls, and both the in-app back action
  and Android Back return to the category index.
- Developer tools remain isolated to QA builds and do not appear in the production menu.

## Validation and publication contract

The feature commit passed 72 repository policy tests, standard and Google-free unit/lint/APK gates,
the 91-artifact Google-free dependency check, and 21 focused Android navigation/feed tests on a
disposable emulator. Protected pull request #63 then passed the complete standard and Google-free
builds, CodeQL, dependency review, open-source security, backend RLS, release policy, live backup,
native archive recovery, and both 42-test Android emulator lanes.

The release commit must independently pass those protected checks. The publisher waits for the
exact main-branch commit, builds the production release with the permanent key, verifies Android 16
native alignment and certificate fingerprint
`44510de2f642f54f8f046fc05b44227a15a2e8473460594b106e976862d3436f`, and refuses to replace any
existing tag or release assets.

No backend schema or Edge Function change is required for 4.2.1. The deployed archive schema and
account-deletion function from 4.2.0 remain the required production backend.

The workflow publishes immutable `DailyBeat-v4.2.1.apk` and `SHA256SUMS.txt` assets. The release
page and workflow runs are the authoritative final publication evidence.

## Remaining operational evidence

Battery consumption, stay-detection recall and OEM background restrictions still require a
multi-day physical-phone trial. No independent penetration audit, uptime SLA or fleet-scale
performance certification is claimed. Preserve local history and the recovery passphrase when
updating.
