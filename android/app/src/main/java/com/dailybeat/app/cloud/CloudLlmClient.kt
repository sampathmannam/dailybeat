package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

open class CloudLlmClient(
    private val apiKeySource: ApiKeySource,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpoints: CloudEndpoints = CloudEndpoints(),
) : CloudTextGenerator {

    private val jsonMedia = "application/json".toMediaType()

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
                ?: CloudProvider.OPENAI

            try {
                when (provider) {
                    CloudProvider.DEEPSEEK -> openAi(
                        apiKey = apiKey,
                        model = settings.cloudModel.ifBlank { CloudProvider.DEEPSEEK.defaultModel },
                        system = systemPrompt,
                        user = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        url = endpoints.deepSeek,
                        provider = provider.displayName,
                    )
                    CloudProvider.ANTHROPIC -> anthropic(
                        apiKey = apiKey,
                        model = settings.cloudModel.ifBlank { CloudProvider.ANTHROPIC.defaultModel },
                        system = systemPrompt,
                        user = userPrompt,
                        maxOutputTokens = maxOutputTokens,
                        url = endpoints.anthropic,
                    )
                    CloudProvider.OPENAI -> openAi(
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

    private fun compatible(
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
        if (parsedBase == null || (!isHttps && !isLoopbackHttp)) {
            return Result.failure(
                IllegalStateException("OpenAI-compatible base URL must be HTTPS in production."),
            )
        }
        return openAi(
            apiKey = apiKey,
            model = settings.cloudModel,
            system = systemPrompt,
            user = userPrompt,
            maxOutputTokens = maxOutputTokens,
            url = "$base/chat/completions",
            provider = provider,
        )
    }

    private fun openAi(
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
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(httpFailure(response.code, provider))
            }
            val responseBody = readBoundedBody(response.body, provider).getOrElse {
                return@use Result.failure(it)
            }
            val json = try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                return@use Result.failure(IllegalStateException("Invalid response from $provider."))
            }
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
            if (content.isNullOrBlank()) {
                return@use Result.failure(IllegalStateException("Empty response from $provider."))
            }
            Result.success(content)
        }
    }

    private fun anthropic(
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
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(
                    httpFailure(response.code, CloudProvider.ANTHROPIC.displayName),
                )
            }
            val responseBody = readBoundedBody(response.body, CloudProvider.ANTHROPIC.displayName)
                .getOrElse { return@use Result.failure(it) }
            val json = try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                return@use Result.failure(IllegalStateException("Invalid response from Anthropic."))
            }
            val content = json.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
            if (content.isNullOrBlank()) {
                return@use Result.failure(IllegalStateException("Empty response from Anthropic."))
            }
            Result.success(content)
        }
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
        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(110, TimeUnit.SECONDS)
            .build()
    }
}
