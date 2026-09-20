package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class CloudLlmClient(
    private val apiKeySource: ApiKeySource,
    httpClient: OkHttpClient = defaultHttpClient(),
    private val endpoints: CloudEndpoints = CloudEndpoints(),
    private val currentSettings: (() -> AppSettings)? = null,
) : CloudTextGenerator {

    private val jsonMedia = "application/json".toMediaType()
    private val httpClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val key = try {
                apiKeySource.getApiKey()
            } catch (error: Exception) {
                throw IOException("Unable to read the cloud API key securely.", error)
            }
            if (key.isNullOrBlank() || (chain.request().header("Authorization") != "Bearer $key" &&
                    chain.request().header("x-api-key") != key)) {
                throw IOException("Cloud API key changed before the request was sent.")
            }
            val requested = chain.request().tag(AppSettings::class.java)
            if (requested != null && currentSettings != null) {
                val current = try {
                    currentSettings.invoke()
                } catch (error: Exception) {
                    throw IOException("Unable to verify current cloud consent.", error)
                }
                if (!current.cloudLlmEnabled || current.cloudProvider != requested.cloudProvider ||
                    current.cloudBaseUrl != requested.cloudBaseUrl || current.cloudModel != requested.cloudModel) {
                    throw IOException("Cloud settings changed before the request was sent.")
                }
            }
            chain.proceed(chain.request())
        }
        .build()

    /** Kept for source compatibility; production call sites should choose an explicit budget. */
    open suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> = generate(
        settings = settings,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        maxOutputTokens = CloudTokenBudgets.DAILY_DIARY,
    )

    override suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): Result<String> {
        require(maxOutputTokens in 1..4_096) { "maxOutputTokens must be between 1 and 4096." }
        return withContext(Dispatchers.IO) {
            val apiKey = runCatching { apiKeySource.getApiKey() }.getOrElse {
                return@withContext Result.failure(
                    IllegalStateException("Unable to read the cloud API key securely."),
                )
            }
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Cloud API key not set. Add it in Settings → Cloud AI."),
                )
            }
            if (!settings.cloudLlmEnabled) {
                return@withContext Result.failure(IllegalStateException("Cloud AI is disabled in Settings."))
            }

            val provider = CloudProvider.entries.find { it.id == settings.cloudProvider }
                ?: return@withContext Result.failure(IllegalStateException("Choose a supported Cloud AI provider."))

            try {
                when (provider) {
                    CloudProvider.DEEPSEEK -> openAi(
                        settings = settings,
                        apiKey = apiKey,
                        model = settings.cloudModel.ifBlank { CloudProvider.DEEPSEEK.defaultModel },
                        system = systemPrompt,
                        user = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        url = endpoints.deepSeek,
                        provider = provider.displayName,
                    )
                    CloudProvider.ANTHROPIC -> anthropic(
                        settings = settings,
                        apiKey = apiKey,
                        model = settings.cloudModel.ifBlank { CloudProvider.ANTHROPIC.defaultModel },
                        system = systemPrompt,
                        user = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        url = endpoints.anthropic,
                    )
                    CloudProvider.OPENAI -> openAi(
                        settings = settings,
                        apiKey = apiKey,
                        model = settings.cloudModel.ifBlank { CloudProvider.OPENAI.defaultModel },
                        system = systemPrompt,
                        user = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        url = endpoints.openAi,
                        provider = provider.displayName,
                    )
                    CloudProvider.COMPATIBLE -> compatible(
                        settings = settings,
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        provider = provider.displayName,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Result.failure(
                    CloudRequestException(
                        provider = provider.displayName,
                        statusCode = null,
                        retryable = true,
                        safeMessage = "${provider.displayName} request failed due to a network error.",
                        cause = error,
                    ),
                )
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    private suspend fun compatible(
        settings: AppSettings,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
        provider: String,
    ): Result<String> {
        val base = settings.cloudBaseUrl.trim().removeSuffix("/")
        if (base.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Set base URL for OpenAI-compatible provider " +
                        "(e.g. https://api.groq.com/openai/v1)",
                ),
            )
        }
        val parsedBase = base.toHttpUrlOrNull()
        val isHttps = parsedBase?.scheme == "https"
        val isLoopbackHttp = parsedBase?.scheme == "http" &&
            parsedBase.host in setOf("localhost", "127.0.0.1", "::1")
        if (parsedBase == null || (!isHttps && !isLoopbackHttp) ||
            parsedBase.username.isNotEmpty() || parsedBase.password.isNotEmpty() ||
            parsedBase.query != null || parsedBase.fragment != null ||
            base.any { it.isISOControl() } || base.length > 2_048) {
            return Result.failure(
                IllegalStateException("OpenAI-compatible base URL must use HTTPS and contain no credentials, query, or fragment."),
            )
        }
        return openAi(
            settings = settings,
            apiKey = apiKey,
            model = settings.cloudModel,
            system = systemPrompt,
            user = userPrompt,
            maxOutputTokens = maxOutputTokens,
            url = parsedBase.newBuilder().addPathSegments("chat/completions").build().toString(),
            provider = provider,
        )
    }

    private suspend fun openAi(
        settings: AppSettings,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        maxOutputTokens: Int,
        url: String,
        provider: String,
    ): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("max_tokens", maxOutputTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        val request = Request.Builder()
            .url(url)
            .tag(AppSettings::class.java, settings)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        val responseBody = responseText(request, provider).getOrElse { return Result.failure(it) }
        val json = try {
            JSONObject(responseBody)
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Invalid response from $provider."))
        }
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
        if (content.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Empty response from $provider."))
        }
        return Result.success(content)
    }

    private suspend fun anthropic(
        settings: AppSettings,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        maxOutputTokens: Int,
        url: String,
    ): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxOutputTokens)
            put("system", system)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        val request = Request.Builder()
            .url(url)
            .tag(AppSettings::class.java, settings)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        val responseBody = responseText(request, CloudProvider.ANTHROPIC.displayName)
            .getOrElse { return Result.failure(it) }
        val json = try {
            JSONObject(responseBody)
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Invalid response from Anthropic."))
        }
        val content = json.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
        if (content.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Empty response from Anthropic."))
        }
        return Result.success(content)
    }

    /** Cancel the socket as well as the coroutine, including a stalled response-body read. */
    private suspend fun responseText(request: Request, provider: String): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) Result.failure(httpFailure(it.code, provider))
                            else readBoundedBody(it.body, provider)
                        }
                        if (!continuation.isCancelled) continuation.resume(result)
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun httpFailure(statusCode: Int, provider: String) = CloudRequestException(
        provider = provider,
        statusCode = statusCode,
        retryable = statusCode == 408 || statusCode == 429 || statusCode in 500..599,
        safeMessage = "$provider request failed (HTTP $statusCode).",
    )

    private fun readBoundedBody(body: ResponseBody?, provider: String): Result<String> {
        if (body == null) return Result.success("")
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Response from $provider was too large."))
        }
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1L)) {
            return Result.failure(IllegalStateException("Response from $provider was too large."))
        }
        return Result.success(source.readUtf8())
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
        // Provider endpoints are fixed and have no reason to redirect. OkHttp strips
        // `Authorization` on a cross-host redirect but not the custom `x-api-key` header the
        // Anthropic path sends, so refuse redirects outright rather than risk replaying a key.
        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(110, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
