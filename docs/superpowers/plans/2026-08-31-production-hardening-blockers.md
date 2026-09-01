# DailyBeat Production-Hardening Blockers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a signed DailyBeat release that rejects impossible AI citations, bounds cloud cost and retries, keeps MapLibre off routine screens, records privacy-safe failures, and passes deterministic CI plus three Motorola QA loops.

**Architecture:** A typed cloud-generation interface carries an explicit token budget and safe retry classification. A pure report validator and a one-correction orchestrator gate every daily diary before persistence. Today uses a Compose-only journey preview; a dedicated destination owns the MapLibre lifecycle. CI separates fast verification from deterministic emulator coverage, while real tiles and the final capped DeepSeek contract are verified on the QA package installed on the Motorola.

**Tech Stack:** Kotlin 1.9, Android 14/API 34, Jetpack Compose, Navigation Compose, Room, WorkManager, OkHttp/MockWebServer, MapLibre 11.8.0, JUnit/Robolectric, Compose instrumentation, Python/pytest, GitHub Actions, ADB, Locust against a local mock only.

## Global Constraints

- Never read, print, export, commit, or transmit the user's stored API key outside the encrypted Android key store and the configured cloud request.
- Never load-test DeepSeek or another billable provider; Locust targets only `tools/locust/mock_deepseek.py`.
- Cloud output limits are exact: connection 32, event extraction 400, midday pulse 400, daily diary 900, weekly rollup 1,200 tokens.
- Invalid report structure receives exactly one immediate correction request and is never scheduled for background retry.
- Only network, timeout, HTTP 429, and provider 5xx failures are retryable; authentication/configuration, other 4xx, empty output, and integrity failures are not.
- Normal Today/Diary/History/Settings navigation must not initialize MapLibre.
- Required CI must not depend on external map tiles completing.
- QA uses `com.dailybeat.app.qa`; production data in `com.dailybeat.app` must not be cleared or modified.
- No third-party telemetry is added. Operational records stay local and exclude keys, prompts, diary text, coordinates, call contents, and model responses.
- Release output remains one permanently signed universal APK plus `SHA256SUMS.txt`, compatible with Obtainium.

---

### Task 1: Typed cloud request contract and token budgets

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/cloud/CloudTextGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/data/settings/SecureApiKeyStore.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/CloudLlmClient.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/ReportGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/PulseReportGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/WeeklyReportGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/llm/EventExtractor.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/diary/DiaryViewModel.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsViewModel.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/cloud/CloudLlmClientTest.kt`

**Interfaces:**
- Produces: `ApiKeySource.getApiKey(): String?`.
- Produces: `CloudTextGenerator.generate(settings, systemPrompt, userPrompt, maxOutputTokens): Result<String>`.
- Produces: `CloudRequestException(provider, statusCode, retryable, safeMessage)`.
- Produces: `CloudTokenBudgets.CONNECTION`, `EVENT_EXTRACTION`, `MIDDAY_PULSE`, `DAILY_DIARY`, and `WEEKLY_ROLLUP`.

- [ ] **Step 1: Write failing HTTP-contract tests**

Create MockWebServer tests that inject a fake key and endpoint, then assert the outbound JSON and safe errors:

```kotlin
@Test
fun deepSeekRequestCarriesExplicitTokenBudget() = runBlocking {
    server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))
    val result = client.generate(settings(), "system", "user", 32)
    val body = JSONObject(server.takeRequest().body.readUtf8())

    assertEquals("ok", result.getOrThrow())
    assertEquals("deepseek-chat", body.getString("model"))
    assertEquals(32, body.getInt("max_tokens"))
}

@Test
fun deepSeek401IsNamedAndNonRetryableWithoutLeakingBody() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(401).setBody("secret-token rejected"))
    val error = client.generate(settings(), "system", "user", 32).exceptionOrNull()
        as CloudRequestException

    assertEquals("DeepSeek", error.provider)
    assertEquals(401, error.statusCode)
    assertFalse(error.retryable)
    assertFalse(error.message.orEmpty().contains("secret-token"))
}
```

- [ ] **Step 2: Run the tests and confirm the old client cannot satisfy them**

Run:

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.cloud.CloudLlmClientTest
```

Expected: compilation failure because the injectable endpoint, token argument, and typed exception do not exist.

- [ ] **Step 3: Add the typed interface and budgets**

Create `CloudTextGenerator.kt` with these exact public contracts:

```kotlin
package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings

fun interface ApiKeySource {
    fun getApiKey(): String?
}

interface CloudTextGenerator {
    suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): Result<String>
}

object CloudTokenBudgets {
    const val CONNECTION = 32
    const val EVENT_EXTRACTION = 400
    const val MIDDAY_PULSE = 400
    const val DAILY_DIARY = 900
    const val WEEKLY_ROLLUP = 1_200
}

data class CloudEndpoints(
    val deepSeek: String = "https://api.deepseek.com/v1/chat/completions",
    val openAi: String = "https://api.openai.com/v1/chat/completions",
    val anthropic: String = "https://api.anthropic.com/v1/messages",
)

class CloudRequestException(
    val provider: String,
    val statusCode: Int?,
    val retryable: Boolean,
    safeMessage: String,
    cause: Throwable? = null,
) : IllegalStateException(safeMessage, cause)
```

Make `SecureApiKeyStore` implement `ApiKeySource`. Make `CloudLlmClient` implement `CloudTextGenerator`, inject `ApiKeySource`, `OkHttpClient`, and `CloudEndpoints` with production defaults, require `maxOutputTokens in 1..4_096`, put `max_tokens` in OpenAI-compatible and Anthropic JSON, wrap IO/timeouts as retryable, and map HTTP 429/5xx to retryable typed errors. Consume each response with `response.use { }`; never include the server body or key in the user-visible error.

- [ ] **Step 4: Give every existing call site an exact budget**

Use these arguments:

```kotlin
maxOutputTokens = CloudTokenBudgets.CONNECTION
maxOutputTokens = CloudTokenBudgets.EVENT_EXTRACTION
maxOutputTokens = CloudTokenBudgets.MIDDAY_PULSE
maxOutputTokens = CloudTokenBudgets.DAILY_DIARY
maxOutputTokens = CloudTokenBudgets.WEEKLY_ROLLUP
```

Connection uses `CONNECTION`; `EventExtractor` uses `EVENT_EXTRACTION`; pulse uses `MIDDAY_PULSE`; both Diary custom generation and daily generation use `DAILY_DIARY`; weekly uses `WEEKLY_ROLLUP`.

- [ ] **Step 5: Run focused and complete Android unit tests**

Run:

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.cloud.CloudLlmClientTest
.\gradlew.bat --no-daemon :app:testDebugUnitTest
```

Expected: all CloudLlmClient tests pass and the full Android unit suite reports `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the cloud contract**

```powershell
git add android/app/src/main/java android/app/src/test/java/com/dailybeat/app/cloud/CloudLlmClientTest.kt
git commit -m "fix(cloud): bound requests and classify failures"
```

---

### Task 2: Pure diary citation integrity validator

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/cloud/ReportIntegrityValidator.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/cloud/ReportIntegrityValidatorTest.kt`

**Interfaces:**
- Produces: `ReportIntegrityCheck(isValid: Boolean, violations: List<String>)`.
- Produces: `ReportIntegrityValidator.validate(report, visitRefCount, eventRefCount): ReportIntegrityCheck`.
- Produces: `ReportIntegrityValidator.correctionPrompt(originalPrompt, invalidReport, violations): String`.

- [ ] **Step 1: Write the failing validator matrix**

Cover the observed live failure and valid boundaries:

```kotlin
@Test
fun rejectsVisitReferencesWhenSourceHasNoVisits() {
    val result = ReportIntegrityValidator.validate(
        "Only one event occurred [E1]. No visits occurred [V1][V2].",
        visitRefCount = 0,
        eventRefCount = 1,
    )
    assertFalse(result.isValid)
    assertEquals(listOf("Unknown citation [V1].", "Unknown citation [V2]."), result.violations)
}

@Test
fun acceptsOnlyReferencesPresentInTheSourceContext() {
    val result = ReportIntegrityValidator.validate(
        "Visited headquarters [V1]. Recorded a briefing [E1].",
        visitRefCount = 1,
        eventRefCount = 1,
    )
    assertTrue(result.isValid)
}

@Test
fun rejectsNarrativeWithoutAnySourceCitationWhenSourcesExist() {
    val result = ReportIntegrityValidator.validate("A briefing occurred.", 0, 1)
    assertFalse(result.isValid)
    assertTrue(result.violations.contains("Report contains no valid source citation."))
}
```

Also test empty output, duplicate valid references, zero-source input, `[V0]`, `[E2]` when only E1 exists, lowercase/non-reference brackets, and deterministic violation order.

- [ ] **Step 2: Run the focused test and verify it fails**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.cloud.ReportIntegrityValidatorTest
```

Expected: compilation failure because `ReportIntegrityValidator` does not exist.

- [ ] **Step 3: Implement the pure validator**

Use this reference extraction and allowed-set logic:

```kotlin
data class ReportIntegrityCheck(
    val isValid: Boolean,
    val violations: List<String>,
)

object ReportIntegrityValidator {
    private val citation = Regex("\\[([VE])(\\d+)]")

    fun validate(report: String, visitRefCount: Int, eventRefCount: Int): ReportIntegrityCheck {
        val violations = linkedSetOf<String>()
        if (report.isBlank()) violations += "Report is empty."
        val allowed = buildSet {
            (1..visitRefCount).forEach { add("[V$it]") }
            (1..eventRefCount).forEach { add("[E$it]") }
        }
        val cited = citation.findAll(report).map { it.value }.toList()
        cited.filterNot(allowed::contains).forEach { violations += "Unknown citation $it." }
        if (allowed.isNotEmpty() && cited.none(allowed::contains)) {
            violations += "Report contains no valid source citation."
        }
        return ReportIntegrityCheck(violations.isEmpty(), violations.toList())
    }

    fun correctionPrompt(
        originalPrompt: String,
        invalidReport: String,
        violations: List<String>,
    ): String = buildString {
        appendLine(originalPrompt)
        appendLine()
        appendLine("CORRECTION REQUIRED:")
        violations.forEach { appendLine("- $it") }
        appendLine("Rewrite the report using only citation IDs present in DATA. Do not explain the correction.")
        appendLine("INVALID REPORT:")
        append(invalidReport.take(6_000))
    }
}
```

- [ ] **Step 4: Run the validator matrix and full unit suite**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.cloud.ReportIntegrityValidatorTest
.\gradlew.bat --no-daemon :app:testDebugUnitTest
```

Expected: focused and full suites pass.

- [ ] **Step 5: Commit the validator**

```powershell
git add android/app/src/main/java/com/dailybeat/app/cloud/ReportIntegrityValidator.kt android/app/src/test/java/com/dailybeat/app/cloud/ReportIntegrityValidatorTest.kt
git commit -m "fix(ai): reject impossible diary citations"
```

---

### Task 3: One-correction generation and nonrecursive retry policy

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/cloud/ValidatedReportClient.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/cloud/ReportRetryPolicy.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/ReportGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/ReportRetryWorker.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/notify/DailyReminderReceiver.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/DailyBeatApp.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/cloud/ValidatedReportClientTest.kt`
- Test: `android/app/src/test/java/com/dailybeat/app/cloud/ReportRetryPolicyTest.kt`

**Interfaces:**
- Consumes: `CloudTextGenerator`, `CloudTokenBudgets.DAILY_DIARY`, and `ReportIntegrityValidator`.
- Produces: `ReportIntegrityException` with `retryable = false` semantics.
- Produces: `ValidatedReportClient.generate(settings, systemPrompt, userPrompt, source): Result<String>`.
- Produces: `ReportRetryPolicy.shouldRetry(Throwable): Boolean`.

- [ ] **Step 1: Write failing orchestration tests with a deterministic fake**

Use a queue-backed fake and prove call counts:

```kotlin
private class FakeCloud(private val replies: ArrayDeque<Result<String>>) : CloudTextGenerator {
    val prompts = mutableListOf<String>()
    override suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): Result<String> {
        assertEquals(CloudTokenBudgets.DAILY_DIARY, maxOutputTokens)
        prompts += userPrompt
        return replies.removeFirst()
    }
}

@Test
fun invalidFirstResponseIsCorrectedOnceAndValidSecondResponseWins() = runBlocking {
    val cloud = FakeCloud(ArrayDeque(listOf(
        Result.success("Invented visit [V1]."),
        Result.success("Recorded event [E1]."),
    )))
    val result = ValidatedReportClient(cloud).generate(
        AppSettings(), "system", "DATA: [E1] note", DayContextBuilder.BuiltContext("", 0, 1),
    )
    assertEquals("Recorded event [E1].", result.getOrThrow())
    assertEquals(2, cloud.prompts.size)
    assertTrue(cloud.prompts.last().contains("Unknown citation [V1]."))
}

@Test
fun repeatedInvalidResponseStopsAfterTwoCalls() = runBlocking {
    val cloud = FakeCloud(ArrayDeque(List(2) { Result.success("Invented [V1].") }))
    val error = ValidatedReportClient(cloud).generate(
        AppSettings(), "system", "DATA: [E1] note", DayContextBuilder.BuiltContext("", 0, 1),
    ).exceptionOrNull()
    assertTrue(error is ReportIntegrityException)
    assertEquals(2, cloud.prompts.size)
}
```

Test retry policy separately: IOException-like typed cloud failures, 429, and 500 are true; 401, 400, and `ReportIntegrityException` are false.

- [ ] **Step 2: Run focused tests and verify red state**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.cloud.ValidatedReportClientTest --tests com.dailybeat.app.cloud.ReportRetryPolicyTest
```

Expected: compilation failure because orchestration types do not exist.

- [ ] **Step 3: Implement the one-correction gate**

Create the exact exception and flow:

```kotlin
class ReportIntegrityException(message: String) : IllegalStateException(message)

class ValidatedReportClient(
    private val cloud: CloudTextGenerator,
) {
    suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        source: DayContextBuilder.BuiltContext,
    ): Result<String> {
        val first = cloud.generate(
            settings, systemPrompt, userPrompt, CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val firstCheck = ReportIntegrityValidator.validate(
            first, source.visitRefCount, source.eventRefCount,
        )
        if (firstCheck.isValid) return Result.success(first)
        val correction = ReportIntegrityValidator.correctionPrompt(
            userPrompt, first, firstCheck.violations,
        )
        val second = cloud.generate(
            settings, systemPrompt, correction, CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val secondCheck = ReportIntegrityValidator.validate(
            second, source.visitRefCount, source.eventRefCount,
        )
        return if (secondCheck.isValid) Result.success(second) else Result.failure(
            ReportIntegrityException(
                "Cloud report failed source-integrity validation: ${secondCheck.violations.joinToString(" ")}",
            ),
        )
    }
}
```

- [ ] **Step 4: Route daily generation through the gate**

Use `DayContextBuilder.buildDetailed`, pass its `text` through `ContextLimiter`, retain the original counts, and call `ValidatedReportClient`. Remove `ReportRetryWorker.enqueue` from `ReportGenerator.onFailure`; the generator must be side-effect-free except for its explicit save method.

Implement policy as:

```kotlin
object ReportRetryPolicy {
    fun shouldRetry(error: Throwable): Boolean =
        (error as? CloudRequestException)?.retryable == true
}
```

`DailyReminderReceiver` enqueues one unique worker only when `shouldRetry` is true. `ReportRetryWorker` returns `Result.retry()` only when the policy is true and `runAttemptCount < 2`; it never enqueues another worker from inside `doWork`.

- [ ] **Step 5: Run all cloud and worker tests**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.dailybeat.app.cloud.*"
.\gradlew.bat --no-daemon :app:testDebugUnitTest
```

Expected: all tests pass; fake cloud call count never exceeds two for invalid content.

- [ ] **Step 6: Commit validated generation**

```powershell
git add android/app/src/main/java/com/dailybeat/app/cloud android/app/src/main/java/com/dailybeat/app/notify/DailyReminderReceiver.kt android/app/src/main/java/com/dailybeat/app/DailyBeatApp.kt android/app/src/test/java/com/dailybeat/app/cloud
git commit -m "fix(ai): validate reports before saving"
```

---

### Task 4: Lightweight Today preview and dedicated MapLibre destination

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/ui/components/JourneyRoutePreview.kt`
- Create: `android/app/src/main/java/com/dailybeat/app/ui/map/JourneyMapScreen.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/components/JourneyMapPreview.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/today/TodayScreen.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/DailyBeatAppScaffold.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Test: `android/app/src/test/java/com/dailybeat/app/ui/components/JourneyMapModelTest.kt`
- Test: `android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt`
- Test: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Produces: `JourneyRoutePreview(visits, onOpenMap, modifier)` with no MapLibre import.
- Produces: `JourneyMapScreen(visits, onBack)` as the only routine UI owner of `MapView`.
- Produces: `Routes.MAP = "journey-map"`.
- Changes: `TodayScreen` gains required callback `onOpenMap: () -> Unit`.

- [ ] **Step 1: Write failing UI/source contracts**

Add a Python contract test proving Today no longer imports or instantiates MapLibre and navigation owns the map route:

```python
def test_today_defers_maplibre_to_dedicated_destination():
    today = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/today/TodayScreen.kt").read_text()
    scaffold = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/DailyBeatAppScaffold.kt").read_text()
    preview = (ROOT / "android/app/src/main/java/com/dailybeat/app/ui/components/JourneyRoutePreview.kt").read_text()

    assert "JourneyMapPreview" not in today
    assert "JourneyRoutePreview" in today
    assert 'const val MAP = "journey-map"' in scaffold
    assert "MapLibre" not in preview
    assert "MapView" not in preview
```

Split `syntheticDayCanBeLoadedRepeatedlyWithoutDuplicatingRecords` so it ends after count assertions. Add a separate navigation test that taps `Open full map`, uses UiAutomator to find the map container/back description, presses back, and then resumes Compose assertions only after the map destination is gone.

- [ ] **Step 2: Run the new contracts and verify failure**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
cd android
.\gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin
```

Expected: Python contract fails because `JourneyRoutePreview.kt` and the map route do not exist; Android test compilation fails on the new route semantics until implementation.

- [ ] **Step 3: Implement the Compose-only preview**

Build `JourneyMapModel.fromVisits(visits)` with `remember(visits)`. Render a 140dp `Canvas` whose route uses point order normalized into the canvas bounds, draw an amber line and coral stop circles, show the point count, and expose a `SecondaryButton` labeled `Open full map`. The file must import only Compose drawing APIs and DailyBeat models/components—no `org.maplibre` package.

Use this stable normalization helper and cover it in `JourneyMapModelTest`:

```kotlin
internal fun previewFractions(pointCount: Int): List<Float> = when {
    pointCount <= 0 -> emptyList()
    pointCount == 1 -> listOf(0.5f)
    else -> List(pointCount) { index -> index.toFloat() / (pointCount - 1).toFloat() }
}
```

The preview is a compact journey sequence, not a geographic tile map; the dedicated destination remains the geographic truth.

- [ ] **Step 4: Move MapLibre ownership to the dedicated screen**

Rename the current `JourneyMapPreview` composable to `JourneyMapView` and call it only from `JourneyMapScreen`. `JourneyMapScreen` renders a back control, title, loading/error states, the 220dp-or-larger map, point count, and external OpenStreetMap action. Preserve the existing lifecycle observer and ensure `onDispose` calls pause, stop, and `onDestroy` once.

Add navigation:

```kotlin
object Routes {
    const val TODAY = "today"
    const val MAP = "journey-map"
    const val DIARY = "diary/{dateKey}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}
```

Pass the existing `todayViewModel.todayVisits` state into `JourneyMapScreen`; do not serialize coordinates into navigation arguments. Hide the bottom bar while `currentRoute == Routes.MAP` and restore it after back navigation.

- [ ] **Step 5: Run map model, source contract, and instrumentation compilation**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.ui.components.JourneyMapModelTest
.\gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin
```

Expected: all commands pass; no MapLibre symbol appears in `TodayScreen.kt` or `JourneyRoutePreview.kt`.

- [ ] **Step 6: Commit map isolation**

```powershell
git add android/app/src/main/java/com/dailybeat/app/ui android/app/src/main/res/values/strings.xml android/app/src/test android/app/src/androidTest scripts/tests/test_release_pipeline.py
git commit -m "perf(map): defer MapLibre to full map"
```

---

### Task 5: Bounded privacy-safe operational failure log

**Files:**
- Create: `android/app/src/main/java/com/dailybeat/app/audit/OperationalFailureLog.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/cloud/ReportGenerator.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/map/JourneyMapScreen.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/com/dailybeat/app/capture/CaptureController.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Test: `android/app/src/test/java/com/dailybeat/app/audit/OperationalFailureLogTest.kt`

**Interfaces:**
- Produces: `OperationalFailureLog.record(context, category, retryable, message)`.
- Produces: `OperationalFailureLog.readRecent(context, maxLines = 40): List<String>`.
- Produces: `OperationalFailureLog.clear(context)`.

- [ ] **Step 1: Write failing privacy and retention tests**

Use Robolectric temporary app storage and assert:

```kotlin
@Test
fun recordRedactsSecretsAndSensitivePayloads() {
    OperationalFailureLog.record(
        context,
        category = "cloud",
        retryable = false,
        message = "Bearer sk-live-secret prompt=private diary lat=12.9716 lon=77.5946",
    )
    val line = OperationalFailureLog.readRecent(context).single()
    assertFalse(line.contains("sk-live-secret"))
    assertFalse(line.contains("private diary"))
    assertFalse(line.contains("12.9716"))
    assertTrue(line.contains("cloud"))
}

@Test
fun recordKeepsOnlyNewestEightyLines() {
    repeat(100) { OperationalFailureLog.record(context, "map", true, "failure-$it") }
    assertEquals(80, OperationalFailureLog.readRecent(context, 100).size)
}
```

- [ ] **Step 2: Run the test and verify red state**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.audit.OperationalFailureLogTest
```

Expected: compilation failure because the log does not exist.

- [ ] **Step 3: Implement bounded sanitized storage**

Store `timestamp | appVersion | category | retryable | sanitized-message` in `filesDir/diagnostics/operational_failures.log`. Sanitize before writing with fixed replacements for bearer tokens, `sk-` tokens, `api key`, `prompt=`, `diary=`, latitude/longitude pairs, CR/LF, and text beyond 160 characters. Rewrite only the newest 80 lines after each append. Catch IO failures so diagnostics never crash the app.

- [ ] **Step 4: Wire confirmed failure boundaries**

Record only caught failures:

- daily report final failure in `ReportGenerator`;
- MapLibre style/render failure in `JourneyMapScreen`;
- backup sign-in/upload/restore failures in `SettingsViewModel`;
- capture start/schedule failures caught by `CaptureController.applyFromSettings`.

Wrap the GPS and call-log application blocks independently so one subsystem still applies if the other throws:

```kotlin
runCatching {
    if (settings.gpsCaptureEnabled && PermissionHelper.canCaptureLocation(context)) {
        LocationService.start(context)
    } else {
        LocationService.stop(context)
    }
}.onFailure { error ->
    OperationalFailureLog.record(context, "capture-gps", false, error.message.orEmpty())
}

runCatching {
    if (settings.callLogEnabled && PermissionHelper.hasCallLog(context)) {
        CallLogWorker.schedule(context)
    } else {
        CallLogWorker.cancel(context)
    }
}.onFailure { error ->
    OperationalFailureLog.record(context, "capture-call-log", false, error.message.orEmpty())
}
```

Expose only the eight newest sanitized records inside the existing debug-only `QA & transparency` group. Do not display this diagnostic list in release UI and do not merge it with raw capture data.

- [ ] **Step 5: Run focused and full tests**

```powershell
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.dailybeat.app.audit.OperationalFailureLogTest
.\gradlew.bat --no-daemon :app:testDebugUnitTest
```

Expected: retention/privacy tests and the full suite pass.

- [ ] **Step 6: Commit local diagnostics**

```powershell
git add android/app/src/main/java/com/dailybeat/app/audit android/app/src/main/java/com/dailybeat/app/capture/CaptureController.kt android/app/src/main/java/com/dailybeat/app/cloud/ReportGenerator.kt android/app/src/main/java/com/dailybeat/app/ui android/app/src/main/res/values/strings.xml android/app/src/test/java/com/dailybeat/app/audit
git commit -m "feat(qa): record privacy-safe failures locally"
```

---

### Task 6: Deterministic split CI with failure artifacts

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt`
- Modify: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Produces: required jobs `verify` and `instrumentation`.
- Produces: artifact `instrumentation-failure-evidence` on emulator failure.

- [ ] **Step 1: Write failing workflow contract tests**

Add assertions:

```python
def test_ci_separates_fast_verification_from_emulator_and_keeps_failure_evidence():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    assert "  verify:" in workflow
    assert "  instrumentation:" in workflow
    assert "python3 -m pytest scripts/tests/ -q" in workflow
    assert "connectedDebugAndroidTest" in workflow
    assert "actions/upload-artifact@v4" in workflow
    assert "instrumentation-failure-evidence" in workflow
```

- [ ] **Step 2: Run the workflow contract and verify failure**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py::test_ci_separates_fast_verification_from_emulator_and_keeps_failure_evidence -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
```

Expected: failure because CI currently has only one `build` job.

- [ ] **Step 3: Split the workflow**

`verify` performs checkout, Java/Android/Gradle setup, `assembleDebug testDebugUnitTest lintDebug`, then installs the Python package and runs `python3 -m pytest scripts/tests/ -q`.

`instrumentation` performs independent setup and uses the existing API 34 x86_64 Nexus 6 emulator. Its script waits for boot/package manager and runs `connectedDebugAndroidTest`. Add an `if: failure()` artifact step containing:

```yaml
path: |
  android/app/build/reports/androidTests/connected/
  android/app/build/outputs/androidTest-results/connected/
  android/app/build/outputs/managed_device_android_test_additional_output/
```

Keep the emulator job timeout at 40 minutes. Do not wait for tile completion in a required test.

- [ ] **Step 4: Verify workflow syntax contracts and local instrumentation compilation**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/ -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
cd android
.\gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin :app:lintDebug
```

Expected: Python suite passes, Android instrumentation sources compile, and lint finishes successfully.

- [ ] **Step 5: Commit CI stabilization**

```powershell
git add .github/workflows/ci.yml android/app/src/androidTest/java/com/dailybeat/app/MainNavigationTest.kt scripts/tests/test_release_pipeline.py
git commit -m "ci: isolate deterministic emulator coverage"
```

---

### Task 7: Adversarial, performance, and Motorola release-candidate gates

**Files:**
- Modify: `tools/locust/locustfile.py`
- Modify: `tools/locust/mock_deepseek.py`
- Modify: `docs/AUDIT_RATING.md`
- Create: `docs/RELEASE_RUNBOOK.md`
- Test: `scripts/tests/test_release_pipeline.py`

**Interfaces:**
- Produces: documented local mock load-test command and thresholds.
- Produces: rollback and last-known-good release procedure.

- [ ] **Step 1: Add deterministic mock scenarios and tests**

The mock server accepts an `X-DailyBeat-Scenario` header with exact scenarios `valid`, `invalid-citations`, `empty`, `rate-limit`, and `server-error`. No scenario reads an external key. Locust mixes valid, 429, and 500 responses and treats only unexpected status/body shapes as failures.

Add a Python test that starts the mock on an ephemeral port, exercises every scenario, and asserts the response contract without internet access.

- [ ] **Step 2: Run the mock tests, then a bounded local load**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/ -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
python tools/locust/mock_deepseek.py --port 8765
```

In a second terminal:

```powershell
locust -f tools/locust/locustfile.py --headless -u 20 -r 5 -t 60s --host http://127.0.0.1:8765 --only-summary
```

Expected: zero unexpected failures. Stop the mock after Locust exits.

- [ ] **Step 3: Build and install the QA APK on the Motorola**

```powershell
cd android
$env:ANDROID_SERIAL='ZD2232FCR5'
.\gradlew.bat --no-daemon :app:installDebug
Remove-Item Env:ANDROID_SERIAL
```

Expected: `Installed on 1 device`; production package remains installed and untouched.

- [ ] **Step 4: Run three critical-path device loops**

For each loop: clear only QA app data for the first loop, complete onboarding, load the synthetic day once, verify the second load adds zero records, open/close the full map three times, rotate portrait/landscape, background/home/resume, navigate Today/Diary/History/Settings, and inspect:

```powershell
adb -s ZD2232FCR5 shell pidof com.dailybeat.app.qa
adb -s ZD2232FCR5 logcat -d -b crash '*:E'
adb -s ZD2232FCR5 shell dumpsys meminfo com.dailybeat.app.qa
```

Expected: live process, no DailyBeat crash entry, no ANR, map route visible, no monotonic retained-memory growth across map cycles, and normal navigation does not initialize the full map.

- [ ] **Step 5: Perform one final capped DeepSeek contract call**

Use the key already stored in the QA package. Generate one synthetic daily diary after all mock tests pass. Verify the app saves no report until every cited `[V#]`/`[E#]` exists in the exact synthetic context. Record only pass/fail, latency, output length, and citation IDs; never record the key, prompt, or full output. Make no further live calls.

- [ ] **Step 6: Update honest ratings and rollback runbook**

`docs/AUDIT_RATING.md` must distinguish fixed code blockers from residual multi-day battery/GPS, device-matrix, external-service SLA, and remote-telemetry gaps. `docs/RELEASE_RUNBOOK.md` must name v3.5.0 as the pre-release last-known-good version, explain GitHub release rollback without deleting history, and list certificate/checksum/install verification commands.

- [ ] **Step 7: Commit evidence documentation and mock hardening**

```powershell
git add tools/locust scripts/tests/test_release_pipeline.py docs/AUDIT_RATING.md docs/RELEASE_RUNBOOK.md
git commit -m "test: add production hardening release gates"
```

---

### Task 8: Integrate icon work, version, verify, and publish stable APK

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `scripts/tests/test_release_pipeline.py`
- Modify: `CHANGELOG.md`
- Modify: `README.md`
- Verify: `.github/workflows/release.yml`

**Interfaces:**
- Produces: Android `versionCode = 12`, `versionName = "3.6.0"`.
- Produces: Git tag/release `v3.6.0` with `app-release.apk` and `SHA256SUMS.txt` only.

- [ ] **Step 1: Ensure the Journey Beat icon PR is green and merged**

```powershell
gh pr checks 5
gh pr view 5 --json state,mergeCommit,url
```

Expected: required checks pass and PR #5 is merged before the hardening branch is finalized. If it remains open or failed, fix that PR independently; do not copy icon commits manually into this branch.

- [ ] **Step 2: Rebase the hardening branch onto final main**

```powershell
git fetch origin
git rebase origin/main
```

Expected: clean rebase including the approved icon. Resolve only genuine overlapping changes and rerun focused tests after any resolution.

- [ ] **Step 3: Write the failing version contract**

Change the Python assertions to require:

```python
assert 'versionCode = 12' in gradle
assert 'versionName = "3.6.0"' in gradle
```

Run:

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest scripts/tests/test_release_pipeline.py::test_android_version_advances_for_obtainium_update -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
```

Expected: failure while Gradle still reports v3.5.0/code 11.

- [ ] **Step 4: Bump version and document the release**

Set code 12/name 3.6.0. Add changelog entries for validated citations, bounded cloud requests/retries, deferred full map, deterministic CI, local diagnostics, Journey Beat icon, and residual field risks. Update README download/version references without claiming the app is bug-free.

- [ ] **Step 5: Run the complete local release gate**

```powershell
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD='1'
python -m pytest -q
Remove-Item Env:PYTEST_DISABLE_PLUGIN_AUTOLOAD
cd android
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
cd ..
git diff --check
git status --short
```

Expected: all tests pass, Gradle reports `BUILD SUCCESSFUL`, diff check is clean, and only intended release files are modified.

- [ ] **Step 6: Commit and open the hardening PR**

```powershell
git add android/app/build.gradle.kts scripts/tests/test_release_pipeline.py CHANGELOG.md README.md
git commit -m "release: prepare DailyBeat v3.6.0"
git push -u origin hardening/production-blockers
gh pr create --base main --head hardening/production-blockers --title "Harden DailyBeat production blockers" --body-file docs/superpowers/specs/2026-08-31-production-hardening-blockers-design.md
```

Expected: PR URL is returned and both `verify` and `instrumentation` jobs run.

- [ ] **Step 7: Merge only after required checks pass, then tag**

```powershell
gh pr checks --watch
gh pr merge --merge --delete-branch
git fetch origin --tags
```

After confirming the merge commit contains version 3.6.0 and all hardening commits:

```powershell
git tag -a v3.6.0 origin/main -m "DailyBeat v3.6.0"
git push origin v3.6.0
$runId = gh run list --workflow "Release APK" --branch v3.6.0 --limit 1 --json databaseId --jq '.[0].databaseId'
gh run watch $runId --exit-status
```

Expected: release workflow succeeds.

- [ ] **Step 8: Verify release assets and signed upgrade**

```powershell
gh release view v3.6.0 --json url,assets
gh release download v3.6.0 --pattern app-release.apk --pattern SHA256SUMS.txt --dir "$env:TEMP\dailybeat-v3.6.0"
Get-FileHash "$env:TEMP\dailybeat-v3.6.0\app-release.apk" -Algorithm SHA256
Get-Content "$env:TEMP\dailybeat-v3.6.0\SHA256SUMS.txt"
adb -s ZD2232FCR5 install -r "$env:TEMP\dailybeat-v3.6.0\app-release.apk"
adb -s ZD2232FCR5 shell am start -W -n com.dailybeat.app/com.dailybeat.app.MainActivity
adb -s ZD2232FCR5 logcat -d -b crash '*:E'
```

Expected: hash matches, upgrade install succeeds without clearing data, cold launch succeeds, crash buffer has no DailyBeat entry, GitHub exposes one APK plus checksum, and Obtainium sees v3.6.0 as newer than v3.5.0.

---

## Final verification checklist

- [ ] No saved diary contains a citation outside its exact source context.
- [ ] Invalid output causes no more than two total provider calls and no WorkManager loop.
- [ ] Every provider request carries the approved explicit token cap.
- [ ] Today and routine tabs do not initialize MapLibre.
- [ ] Full OpenStreetMap renders the synthetic journey on the Motorola.
- [ ] Three QA loops produce no DailyBeat crash/ANR or monotonic map-memory growth.
- [ ] Python, Android unit, lint, build, instrumentation compilation, and GitHub required checks pass.
- [ ] Operational diagnostics contain no key, prompt, diary, coordinates, call content, or response body.
- [ ] v3.6.0 APK certificate and SHA-256 checksum match, upgrade install succeeds, and only one stable APK is published.
- [ ] Release notes retain honest residual risks for field GPS/battery, device matrix, external SLAs, and remote telemetry.
