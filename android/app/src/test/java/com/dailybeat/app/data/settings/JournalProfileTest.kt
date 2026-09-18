package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalProfileTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
    @Before fun before() { prefs.edit().clear().commit() }
    @After fun after() { prefs.edit().clear().commit() }
    @Test fun newInstallIsPersonalAndCloudIsOptIn() {
        val value = SettingsRepository(context).get()
        assertEquals(JournalProfile.PERSONAL, value.journalProfile)
        assertFalse(value.cloudLlmEnabled)
        assertFalse(value.autoEveningReport)
        assertEquals("", value.officerName)
    }
    @Test fun legacyNameKeepsPoliceTemplateUntilExplicitlyChanged() {
        val repo = SettingsRepository(context)
        repo.setOfficerName("Existing officer")
        assertEquals(JournalProfile.POLICE, repo.get().journalProfile)
        repo.setJournalProfile(JournalProfile.FIELD_WORK)
        assertEquals(JournalProfile.FIELD_WORK, SettingsRepository(context).get().journalProfile)
        assertEquals("Existing officer", repo.get().officerName)
    }
    @Test fun savedPersonalTemplateDoesNotBecomePoliceWhenNameIsAdded() {
        val repo = SettingsRepository(context)
        repo.setJournalProfile(JournalProfile.PERSONAL)
        repo.setOfficerName("Alex")
        assertEquals(JournalProfile.PERSONAL, SettingsRepository(context).get().journalProfile)
    }
    @Test fun oldAutomaticReportPreferenceRequiresFreshOptIn() {
        prefs.edit().putBoolean("auto_evening_report", true).commit()
        val repo = SettingsRepository(context)
        assertFalse(repo.get().autoEveningReport)
        repo.setAutoEveningReport(true)
        assertTrue(repo.get().autoEveningReport)
    }
}
