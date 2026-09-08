# DSR separation and DailyBeat reliability pass

Work remains on `hardening/reliability-pass`. No merge, tag or release is authorized without
the owner's approval. Production `com.dailybeat.app` and regular QA `com.dailybeat.app.qa`
are never reset or replaced by instrumentation.

## Implemented in this pass

- Extracted active DSR source, UI, tests and roadmap to private `sampathmannam/dsr`.
- Removed DailyBeat's DSR tab, route, application services and PDFBox dependency.
- Retained legacy Room entities, DAO, migration chain and private PDFs; no destructive migration.
- Restricted all destructive test entry points to `com.dailybeat.app.qa.e2eloop`.
- Kept the diary editor available after clearing text; manual offline entries can be rewritten.
- Corrected week exports to seven calendar days ending today, excluding old/future records.
- Added phone regressions for diary recreation, native PDF/ZIP export, local backup and map navigation.
- Added an independent offline CI device-test job; missing live-backup credentials no longer
  prevent the core tests from running. The mandatory live release gate cannot be bypassed.

## Feature coverage and remaining gates

| Feature | Automated coverage | Required real-device / live check |
| --- | --- | --- |
| Onboarding and navigation | OnboardingFlowTest, MainNavigationTest; four tabs, no DSR | Run on ZD2232FCR5 |
| GPS capture and visits | CaptureLifecycleTest, VisitTracker/AdversarialTest, GeofenceMatcherTest | Start/stop lifecycle plus a real movement/background session |
| Today, notes and moments | MainNavigationTest, EventRepository tests, SyntheticDayGeneratorTest | Note edit/save and significant moment flow |
| Voice notes | Speech orchestration inspection, extraction/error-path tests | Microphone, installed recognition service, transcription and save |
| Diary editing | DiaryClearingTest, DailyBeatReliabilityTest | Clear/rewrite/recreate regression |
| Cloud daily reports | ReportGenerator, integrity, retry, adversarial HTTP tests | Dedicated test AI key and provider connection |
| Feed and place naming | FeedScreenTest, DayFeedBuilder, NamePlace, RetroactiveNaming tests | Re-entry refresh, all stops, edit/name persistence |
| Journey map | JourneyMapModelTest, DailyBeatReliabilityTest | Map rendering, back/recreation and network failure |
| Weekly reports | WeeklyReportGeneratorTest and Locust synthetic contract | Dedicated test AI key and provider response |
| PDF/ZIP sharing | PdfExporterTest, WeekExportSelectionTest, DailyBeatReliabilityTest | Native PDF renderer, ZIP contents and Android share target |
| Settings and secure key | MainNavigationTest, input/permission tests | Valid settings persistence and encrypted-store behavior |
| Backup/restore | Codec, compatibility, coordinator, store, HTTP tests and local phone round trip | Dedicated QA Supabase account; never production |
| Reminders/boot | BootReceiverTest and capture lifecycle | Notification permission, scheduled trigger and device reboot |
| Upgrade/data retention | MigrationTest, MigrationChainTest, DatabasePolicyTest | Isolated upgrade rehearsal; never clear production to test |

Unit/integration coverage does not mean every physical interaction has passed. HTTP mocks do not
validate a production service's capacity or availability. Record each actual run and any skipped
live check separately; never describe the app as guaranteed bug-free.

## Validation commands

```sh
cd android
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug \
  -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop
cd ..
pytest -q scripts/tests
./scripts/mac_phone_e2e.sh ZD2232FCR5
```

The phone runner removes only its explicitly disposable QA app. Do not target the regular QA
app: the owner's imported DSR PDF is still there. Live cloud tests require the dedicated QA
credentials documented in RELEASE.md; absent credentials must remain an explicit validation gap.

## Actual run results — 2026-09-08

- Debug app and instrumentation APK builds: passed.
- JVM/unit/integration suite: **172 passed, 0 failed** after DSR-only tests moved repositories.
- Android lint: passed; existing warnings remain (not a zero-warning claim).
- Python runner/release safety suite: **16 passed, 0 failed**.
- Locust against the loopback-only synthetic API: 20 users / 1,615 requests and 50 users /
  3,965 requests, both 20-second runs, **0 failures**. This is not a live-provider capacity result.
- `ZD2232FCR5`: connected for read-only checks, then disconnected before installation. New
  device regressions compiled but were **not run** on the phone in this pass.
- Shared emulator: system/System UI ANRs; not reset or treated as valid phone evidence.
- Live backup configuration and dedicated account credentials were absent, so live verification
  remains pending. Do not weaken the release gate or use production credentials to bypass this.
- Production remained version 3.5.0, last updated 2026-08-31 01:04:24. The QA PDF's SHA-256
  matched its stored content hash. No production or regular QA data was cleared or rewritten.

Reconnect the phone to complete the feature-by-feature device pass, visual/permission review,
real movement and voice tests, then run another clean regression loop before requesting release.
