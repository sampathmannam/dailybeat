package com.dailybeat.app.data.settings

data class AppSettings(
    val officerName: String = "IPS Officer",
    val gpsCaptureEnabled: Boolean = true,
    val callLogEnabled: Boolean = false,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = CloudProvider.OPENAI.id,
    val cloudModel: String = "gpt-4o-mini",
    val cloudBaseUrl: String = "",
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
)

enum class CloudProvider(val id: String, val displayName: String, val defaultModel: String) {
    OPENAI("openai", "OpenAI", "gpt-4o-mini"),
    ANTHROPIC("anthropic", "Anthropic", "claude-3-5-haiku-20241022"),
    COMPATIBLE("compatible", "OpenAI-compatible (Groq, OpenRouter…)", "gpt-4o-mini"),
}
