package com.dailybeat.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class PermissionPolicyTest {

    private val activity = File("src/main/java/com/dailybeat/app/MainActivity.kt").readText()

    @Test
    fun startupPermissionBatchExcludesOptionalMicrophoneAndCallLog() {
        val requestBody = activity.substringAfter("private fun requestRuntimePermissions()")
            .substringBefore("private fun requestBackgroundLocationIfNeeded()")

        assertFalse("Manifest.permission.RECORD_AUDIO" in requestBody)
        assertFalse("Manifest.permission.READ_CALL_LOG" in requestBody)
    }

    @Test
    fun returningUsersAreNotPromptedOnEveryLaunch() {
        val postContentSetup = activity.substringAfter("setContent {")

        assertFalse("if (!showOnboarding)" in postContentSetup)
    }
}
