package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudLlmClient(
    private val apiKeySource: ApiKeySource,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpoints: CloudEndpoints = CloudEndpoints(),
) : CloudTextGenerator {

    private val jsonMedia = "application/json".toMediaType()

    override suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): Result<String> {
        require(maxOutputTokens in 1..4_096) { "maxOutputTokens must be between 1 and 4096." }
        return withContext(Dispatchers.IO) {
            val apiKey = apiKeySource.getApiKey()
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Cloud API key not set. Add it in Settings → Cloud AI."))
            }
            if (!settings.cloudLlmEnabled) {
                return@withContext Result.failure(IllegalStateException("Cloud AI is disabled in Settings."))
            }

            val provider = CloudProvider.entries.find { it.id == settings.cloudProvider }
                ?: CloudProvider.OPENAI

            try {
                when (provider) {
                    CloudProvider.DEEPSEEK -> openAi(
                        apiKey,
                        settings.cloudModel.ifBlank { CloudProvider.DEEPSEEK.defaultModel },
                        systemPrompt,
                        userPrompt,
                        maxOutputTokens,
                        endpoints.deepSeek,
                        provider.displayName,
                    )
                    CloudProvider.ANTHROPIC -> anthropic(
                        apiKey,
                        settings.cloudModel,
                        systemPrompt,
                        userPrompt,
                        maxOutputTokens,
                        endpoints.anthropic,
                    )
                    CloudProvider.OPENAI -> openAi(
                        apiKey,
                        settings.cloudModel,
                        systemPrompt,
                        userPrompt,
                        maxOutputTokens,
                        endpoints.openAi,
                        provider.displayName,
                    )
                    CloudProvider.COMPATIBLE -> {
                        val base = settings.cloudBaseUrl.trim().removeSuffix("/")
                        if (base.isBlank()) {
                            return@withContext Result.failure(
                                IllegalStateException("Set base URL for OpenAI-compatible provider (e.g. https://api.groq.com/openai/v1)"),
                            )
                        }
                        openAi(
                            apiKey,
                            settings.cloudModel,
                            systemPrompt,
                            userPrompt,
                            maxOutputTokens,
                            "$base/chat/completions",
                            provider.displayName,
                        )
                    }
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
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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
            val responseBody = response.body?.string() ?: ""
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
                return@use Result.failure(IllegalStateException("Empty response from cloud model."))
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
                return@use Result.failure(httpFailure(response.code, CloudProvider.ANTHROPIC.displayName))
            }
            val responseBody = response.body?.string() ?: ""
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
        retryable = statusCode == 429 || statusCode in 500..599,
        safeMessage = "$provider request failed (HTTP $statusCode).",
    )

    companion object {
        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
