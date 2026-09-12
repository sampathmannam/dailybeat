package com.dailybeat.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    private val activity = File("src/main/java/com/dailybeat/app/MainActivity.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun startupPermissionBatchExcludesOptionalMicrophone() {
        val requestBody = activity.substringAfter("private fun requestRuntimePermissions()")
            .substringBefore("private fun requestBackgroundLocationIfNeeded()")

        assertFalse("Manifest.permission.RECORD_AUDIO" in requestBody)
    }

    @Test
    fun returningUsersAreNotPromptedOnEveryLaunch() {
        val postContentSetup = activity.substringAfter("setContent {")

        assertFalse("if (!showOnboarding)" in postContentSetup)
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        assertEquals(1, Regex("android:exported=\"true\"").findAll(manifest).count())
        val mainActivity = manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertTrue("android:exported=\"true\"" in mainActivity)
        assertTrue("android.intent.action.MAIN" in mainActivity)
        assertTrue("android.intent.category.LAUNCHER" in mainActivity)
    }
}
