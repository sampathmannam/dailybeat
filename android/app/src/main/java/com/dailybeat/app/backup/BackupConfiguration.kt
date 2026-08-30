package com.dailybeat.app.backup

data class BackupConfiguration(
    val supabaseUrl: String,
    val anonymousKey: String,
) {
    val baseUrl: String = supabaseUrl.trim().trimEnd('/')
    val isConfigured: Boolean = anonymousKey.isNotBlank() && (
        baseUrl.startsWith("https://") ||
            baseUrl.startsWith("http://localhost:") ||
            baseUrl.startsWith("http://127.0.0.1:")
        )
}
