package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.data.settings.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudLlmClient(
    private val apiKeyStore: SecureApiKeyStore,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    suspend fun generate(settings: AppSettings, systemPrompt: String, userPrompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyStore.getApiKey()
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
                    CloudProvider.ANTHROPIC -> anthropic(apiKey, settings.cloudModel, systemPrompt, userPrompt)
                    CloudProvider.OPENAI -> openAi(apiKey, settings.cloudModel, systemPrompt, userPrompt, OPENAI_URL)
                    CloudProvider.COMPATIBLE -> {
                        val base = settings.cloudBaseUrl.trim().removeSuffix("/")
                        if (base.isBlank()) {
                            return@withContext Result.failure(
                                IllegalStateException("Set base URL for OpenAI-compatible provider (e.g. https://api.groq.com/openai/v1)"),
                            )
                        }
                        openAi(apiKey, settings.cloudModel, systemPrompt, userPrompt, "$base/chat/completions")
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun openAi(
        apiKey: String,
        model: String,
        system: String,
        user: String,
        url: String,
    ): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
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

        val response = http.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            return Result.failure(IllegalStateException("OpenAI API ${response.code}: ${responseBody.take(300)}"))
        }
        val json = JSONObject(responseBody)
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
        if (content.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Empty response from cloud model."))
        }
        return Result.success(content)
    }

    private fun anthropic(
        apiKey: String,
        model: String,
        system: String,
        user: String,
    ): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("system", system)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        val request = Request.Builder()
            .url(ANTHROPIC_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        val response = http.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            return Result.failure(IllegalStateException("Anthropic API ${response.code}: ${responseBody.take(300)}"))
        }
        val json = JSONObject(responseBody)
        val content = json.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
        if (content.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Empty response from Anthropic."))
        }
        return Result.success(content)
    }

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    }
}
