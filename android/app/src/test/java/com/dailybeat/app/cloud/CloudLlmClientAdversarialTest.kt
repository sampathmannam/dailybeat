package com.dailybeat.app.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The officer's diary depends on a third-party LLM endpoint that can rate limit, fail, hang up,
 * or answer with nonsense. Every one of those must come back as a message they can act on,
 * never as a crash and never as a silently empty diary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudLlmClientAdversarialTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CloudLlmClient
    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        server = MockWebServer()
        server.start()
        client = CloudLlmClient(InMemoryApiKeyStore(context))
        settings = AppSettings(
            cloudLlmEnabled = true,
            cloudProvider = CloudProvider.COMPATIBLE.id,
            cloudModel = "test-model",
            cloudBaseUrl = server.url("/v1").toString(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun generate() = runBlocking {
        client.generate(settings, "system", "user")
    }

    private fun assertFailsWithMessage(result: Result<String>, vararg expected: String) {
        assertTrue("Expected a failure, got: $result", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("Message must be non-empty so the officer sees something", message.isNotBlank())
        expected.forEach {
            assertTrue("Expected \"$it\" in \"$message\"", message.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `a normal answer is returned`() {
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"content":"  Diary text.  "}}]}"""),
        )

        assertEquals("Diary text.", generate().getOrNull())
    }

    @Test
    fun `an invalid key is reported, not swallowed`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))

        assertFailsWithMessage(generate(), "401")
    }

    @Test
    fun `rate limiting is reported`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))

        assertFailsWithMessage(generate(), "429")
    }

    @Test
    fun `a provider outage is reported`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream down"))

        assertFailsWithMessage(generate(), "503")
    }

    @Test
    fun `a truncated connection fails cleanly instead of crashing`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertTrue(generate().isFailure)
    }

    @Test
    fun `malformed json fails cleanly`() {
        server.enqueue(MockResponse().setBody("this is not json"))

        assertTrue(generate().isFailure)
    }

    @Test
    fun `an answer with no choices is not saved as an empty diary`() {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))

        assertFailsWithMessage(generate(), "Empty response")
    }

    @Test
    fun `a whitespace-only answer is treated as empty`() {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"   "}}]}"""))

        assertFailsWithMessage(generate(), "Empty response")
    }

    @Test
    fun `an enormous answer is still handled`() {
        val huge = "x".repeat(2_000_000)
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"$huge"}}]}"""))

        val result = generate()
        assertTrue("A large but valid answer should succeed", result.isSuccess)
        assertEquals(huge.length, result.getOrNull()?.length)
    }

    @Test
    fun `a missing api key is explained rather than sent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keyless = CloudLlmClient(InMemoryApiKeyStore(context, key = null))

        val result = runBlocking { keyless.generate(settings, "system", "user") }

        assertFailsWithMessage(result, "API key")
        assertEquals("No request may be sent without a key", 0, server.requestCount)
    }

    @Test
    fun `cloud disabled is explained rather than sent`() {
        val result = runBlocking {
            client.generate(settings.copy(cloudLlmEnabled = false), "system", "user")
        }

        assertFailsWithMessage(result, "disabled")
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a compatible provider with no base url is explained`() {
        val result = runBlocking {
            client.generate(settings.copy(cloudBaseUrl = "   "), "system", "user")
        }

        assertFailsWithMessage(result, "base URL")
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the api key is sent as a header and never in the url`() {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        generate()

        val request = server.takeRequest()
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
        assertTrue(
            "A key in the URL leaks through logs and proxies: ${request.path}",
            !request.path.orEmpty().contains("test-api-key"),
        )
    }
}
