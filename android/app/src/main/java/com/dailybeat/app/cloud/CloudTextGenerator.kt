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
