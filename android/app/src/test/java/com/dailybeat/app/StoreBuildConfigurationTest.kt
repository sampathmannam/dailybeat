package com.dailybeat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class StoreBuildConfigurationTest {
    @Test fun storeBuildNeverEmbedsManagedCredentialsOrGoogleBackend() {
        assumeTrue(BuildConfig.STORE_DISTRIBUTION)
        assertFalse(BuildConfig.GOOGLE_LOCATION)
        assertEquals("", BuildConfig.SUPABASE_URL)
        assertEquals("", BuildConfig.SUPABASE_ANON_KEY)
    }
}
