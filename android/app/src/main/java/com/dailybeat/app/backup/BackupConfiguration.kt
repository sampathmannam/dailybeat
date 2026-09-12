package com.dailybeat.app.backup

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BackupConfiguration(
    val supabaseUrl: String,
    val anonymousKey: String,
) {
    private val parsedUrl: HttpUrl? = supabaseUrl.trim().toHttpUrlOrNull()
    private val hasSafeEndpoint: Boolean = parsedUrl?.let { url ->
        val isManagedSupabaseHttps = url.isHttps &&
            url.host.endsWith(MANAGED_SUPABASE_SUFFIX) &&
            url.host.length > MANAGED_SUPABASE_SUFFIX.length
        val isLoopbackHttp = url.scheme == "http" && url.host in LOOPBACK_HOSTS
        val isRootEndpoint = url.encodedPath == "/" && url.query == null && url.fragment == null
        val hasNoUserInfo = url.username.isEmpty() && url.password.isEmpty()
        (isManagedSupabaseHttps || isLoopbackHttp) && isRootEndpoint && hasNoUserInfo
    } == true
    private val hasSafeAnonymousKey: Boolean = anonymousKey.isNotBlank() &&
        anonymousKey.length <= MAX_ANONYMOUS_KEY_LENGTH &&
        anonymousKey.none(Char::isISOControl)

    val baseUrl: String = parsedUrl
        ?.takeIf { hasSafeEndpoint }
        ?.toString()
        ?.removeSuffix("/")
        .orEmpty()
    val isConfigured: Boolean = hasSafeEndpoint && hasSafeAnonymousKey

    private companion object {
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
        const val MANAGED_SUPABASE_SUFFIX = ".supabase.co"
        const val MAX_ANONYMOUS_KEY_LENGTH = 8_192
    }
}
