package com.dailybeat.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConfigurationTest {

    @Test
    fun `accepts a canonical HTTPS Supabase origin`() {
        val configuration = BackupConfiguration(
            " https://project.supabase.co/ ",
            "public-anon-key",
        )

        assertTrue(configuration.isConfigured)
        assertEquals("https://project.supabase.co", configuration.baseUrl)
    }

    @Test
    fun `accepts HTTP only for loopback development`() {
        assertTrue(BackupConfiguration("http://127.0.0.1:54321/", "key").isConfigured)
        assertTrue(BackupConfiguration("http://[::1]:54321/", "key").isConfigured)
        assertFalse(BackupConfiguration("http://example.com/", "key").isConfigured)
    }

    @Test
    fun `rejects URL confusion and credential injection`() {
        val unsafeUrls = listOf(
            "https://project.supabase.co.evil.example/",
            "https://project.supabase.co/rest/v1",
            "https://project.supabase.co/?redirect=https://evil.example",
            "https://user:password@project.supabase.co/",
            "javascript:alert(1)",
        )

        unsafeUrls.forEach { url ->
            assertFalse(url, BackupConfiguration(url, "key").isConfigured)
        }
    }

    @Test
    fun `rejects missing oversized or control character keys`() {
        assertFalse(BackupConfiguration("https://project.supabase.co", " ").isConfigured)
        assertFalse(BackupConfiguration("https://project.supabase.co", "x".repeat(8_193)).isConfigured)
        assertFalse(BackupConfiguration("https://project.supabase.co", "key\nvalue").isConfigured)
    }
}
