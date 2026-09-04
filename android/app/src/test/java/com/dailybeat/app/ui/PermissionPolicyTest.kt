package com.dailybeat.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class PermissionPolicyTest {

    private val activity = File("src/main/java/com/dailybeat/app/MainActivity.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun startupPermissionBatchExcludesOptionalMicrophoneAndCallLog() {
        val requestBody = activity.substringAfter("private fun requestRuntimePermissions()")
            .substringBeforeLast("\n}")

        assertFalse("Manifest.permission.RECORD_AUDIO" in requestBody)
        assertFalse("Manifest.permission.READ_CALL_LOG" in requestBody)
        assertFalse("Manifest.permission.ACCESS_BACKGROUND_LOCATION" in requestBody)
    }

    @Test
    fun returningUsersAreNotPromptedOnEveryLaunch() {
        val postContentSetup = activity.substringAfter("setContent {")

        assertFalse("if (!showOnboarding)" in postContentSetup)
    }

    @Test
    fun patrolGridManifestExcludesLegacySensitivePermissions() {
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in manifest)
        assertFalse("android.permission.READ_CALL_LOG" in manifest)
        assertFalse("android.permission.RECORD_AUDIO" in manifest)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in manifest)
    }
}
