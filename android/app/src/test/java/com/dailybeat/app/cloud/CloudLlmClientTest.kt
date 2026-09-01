package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
class CloudLlmClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CloudLlmClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = clientWith(
            httpClient = OkHttpClient(),
            endpoints = CloudEndpoints(
                deepSeek = server.url("/deepseek").toString(),
                openAi = server.url("/openai").toString(),
                anthropic = server.url("/anthropic").toString(),
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun deepSeekRequestCarriesExplicitTokenBudget() = runBlocking {
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"ok"}}]}"""))

        val result = client.generate(settings(), "system", "user", 32)
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("ok", result.getOrThrow())
        assertEquals("/deepseek", request.path)
        assertEquals("deepseek-chat", body.getString("model"))
        assertEquals(32, body.getInt("max_tokens"))
    }

    @Test
    fun openAiRequestCarriesExplicitTokenBudget() = runBlocking {
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"ok"}}]}"""))

        client.generate(settings(CloudProvider.OPENAI), "system", "user", 400).getOrThrow()
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("/openai", request.path)
        assertEquals(400, body.getInt("max_tokens"))
    }

    @Test
    fun anthropicRequestCarriesExplicitTokenBudget() = runBlocking {
        server.enqueue(jsonResponse("""{"content":[{"text":"ok"}]}"""))

        client.generate(settings(CloudProvider.ANTHROPIC), "system", "user", 1_200).getOrThrow()
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertEquals("/anthropic", request.path)
        assertEquals(1_200, body.getInt("max_tokens"))
    }

    @Test
    fun compatibleProviderUsesOpenAiCompatRouteWithHttpsEndpoint() = runBlocking {
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"ok"}}]}"""))

        client.generate(
            settings(
                provider = CloudProvider.COMPATIBLE,
                cloudBaseUrl = server.url("/compatible").toString(),
            ),
            "system",
            "user",
            32,
        ).getOrThrow()
        val request = server.takeRequest()

        assertTrue(request.path?.contains("/chat/completions") == true)
        assertEquals("Bearer fake-key", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun compatibleProviderRejectsInsecureEndpoint() = runBlocking {
        val error = client.generate(
            settings(
                provider = CloudProvider.COMPATIBLE,
                cloudBaseUrl = "http://insecure.example.com",
            ),
            "system",
            "user",
            32,
        ).exceptionOrNull() as IllegalStateException

        assertEquals("OpenAI-compatible base URL must be HTTPS in production.", error.message)
    }

    @Test
    fun compatibleProviderRejectsLocalhostUserInfoSpoof() = runBlocking {
        val error = client.generate(
            settings(
                provider = CloudProvider.COMPATIBLE,
                cloudBaseUrl = "http://localhost:8080@evil.example",
            ),
            "system",
            "user",
            32,
        ).exceptionOrNull() as IllegalStateException

        assertEquals("OpenAI-compatible base URL must be HTTPS in production.", error.message)
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

    @Test
    fun malformedSuccessDoesNotLeakResponseBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("secret-token malformed response"))

        val message = client.generate(settings(), "system", "user", 32)
            .exceptionOrNull()?.message.orEmpty()

        assertFalse(message.contains("secret-token"))
    }

    @Test
    fun http429IsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("private quota details"))

        val error = client.generate(settings(CloudProvider.OPENAI), "system", "user", 32)
            .exceptionOrNull() as CloudRequestException

        assertEquals("OpenAI", error.provider)
        assertEquals(429, error.statusCode)
        assertTrue(error.retryable)
        assertFalse(error.message.orEmpty().contains("private quota details"))
    }

    @Test
    fun http5xxIsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("private outage details"))

        val error = client.generate(settings(CloudProvider.ANTHROPIC), "system", "user", 32)
            .exceptionOrNull() as CloudRequestException

        assertEquals("Anthropic", error.provider)
        assertEquals(503, error.statusCode)
        assertTrue(error.retryable)
        assertFalse(error.message.orEmpty().contains("private outage details"))
    }

    @Test
    fun timeoutIsRetryableWithoutAnHttpStatus() = runBlocking {
        val timeoutClient = clientWith(
            httpClient = OkHttpClient.Builder()
                .addInterceptor { throw SocketTimeoutException("private network detail") }
                .build(),
        )

        val error = timeoutClient.generate(settings(), "system", "user", 32)
            .exceptionOrNull() as CloudRequestException

        assertEquals("DeepSeek", error.provider)
        assertNull(error.statusCode)
        assertTrue(error.retryable)
        assertFalse(error.message.orEmpty().contains("private network detail"))
    }

    @Test
    fun tokenBudgetMustBeWithinSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.generate(settings(), "system", "user", 0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.generate(settings(), "system", "user", 4_097) }
        }
    }

    private fun clientWith(
        httpClient: OkHttpClient = OkHttpClient(),
        endpoints: CloudEndpoints = CloudEndpoints(),
    ) = CloudLlmClient(
        apiKeySource = ApiKeySource { "fake-key" },
        httpClient = httpClient,
        endpoints = endpoints,
    )

    private fun settings(
        provider: CloudProvider = CloudProvider.DEEPSEEK,
        cloudBaseUrl: String = "",
    ) = AppSettings(
        cloudLlmEnabled = true,
        cloudProvider = provider.id,
        cloudModel = provider.defaultModel,
        cloudBaseUrl = cloudBaseUrl,
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
