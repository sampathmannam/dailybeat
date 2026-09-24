package com.dailybeat.app.ui.settings

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class SettingsPlaceSafetyTest {
    private lateinit var app: DailyBeatApp
    private lateinit var model: SettingsViewModel
    private val store = ViewModelStore()

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
        app.settingsRepository.setOfficerName("Place safety test")
    }

    @After fun cleanup() = runBlocking {
        store.clear()
        shadowOf(Looper.getMainLooper()).idle()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    private fun openSettings() {
        model = SettingsViewModel(app)
        store.put("settings", model)
        await { model.uiState.value.officerName == "Place safety test" }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < deadline)
        assertTrue("Settings operation did not finish", condition())
    }

    @Test fun `normal place creation privacy and deletion still work`() = runBlocking {
        openSettings()
        model.updatePlaceDraft("Home", "12.9", "77.6")
        model.addPlace()
        await { !model.uiState.value.placeBusy && model.uiState.value.places.size == 1 }
        val saved = model.uiState.value.places.single()
        model.setPlacePrivate(saved, true)
        await { !model.uiState.value.placeBusy }
        assertTrue(app.placeRepository.all().single().isPrivate)
        model.deletePlace(model.uiState.value.places.single())
        await { !model.uiState.value.placeBusy }
        assertTrue(app.placeRepository.all().isEmpty())
        assertNull(model.uiState.value.placeError)
    }

    @Test fun `place write queued before erase cannot recreate old coordinates`() = runBlocking {
        openSettings()
        model.updatePlaceDraft("Erased home", "12.9", "77.6")
        CaptureStorageGate.mutex.lock()
        try {
            model.addPlace()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(model.uiState.value.placeBusy)
            CaptureStorageGate.invalidatePersonalData()
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
        } finally { CaptureStorageGate.mutex.unlock() }
        await { !model.uiState.value.placeBusy && model.uiState.value.placeName.isEmpty() }
        assertTrue(app.placeRepository.all().isEmpty())
        assertEquals("", model.uiState.value.placeLat)
        assertEquals("", model.uiState.value.placeLon)
        assertTrue(model.uiState.value.places.isEmpty())
    }

    @Test fun `stale delete cannot affect identical row restored with the same identifier`() = runBlocking {
        app.placeRepository.add("Home", 12.9, 77.6)
        openSettings()
        val stale = model.uiState.value.places.single()
        CaptureStorageGate.mutex.lock()
        try {
            withContext(Dispatchers.IO) {
                app.db.places().deleteAll()
                app.db.places().insert(stale)
            }
            CaptureStorageGate.invalidatePersonalData()
            // Tap before the dataChanges collector can refresh the screen.
            model.deletePlace(stale)
        } finally { CaptureStorageGate.mutex.unlock() }
        await { !model.uiState.value.placeBusy }
        assertEquals(listOf(stale), app.placeRepository.all())
        assertNotNull(model.uiState.value.placeError)
    }

    @Test fun `privacy toggle based on stale row does not revert a newer name`() = runBlocking {
        app.placeRepository.add("Office", 12.9, 77.6)
        openSettings()
        val stale = model.uiState.value.places.single()
        val renamed = stale.copy(name = "Clinic", isPrivate = true)
        app.db.places().update(renamed)
        model.setPlacePrivate(stale, false)
        await { !model.uiState.value.placeBusy }
        assertEquals(renamed, app.placeRepository.all().single())
        assertNotNull(model.uiState.value.placeError)
    }

    @Test fun `suggested place must still be supported by visible visits when saved`() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(3) { index ->
            app.db.visits().insert(LocationVisit(startMs = now - (index + 1) * 3_600_000L,
                endMs = now - (index + 1) * 3_600_000L + 600_000,
                latitude = 12.9, longitude = 77.6, placeName = "Gym"))
        }
        openSettings()
        val suggestion = model.uiState.value.placeSuggestions.single()
        val visit = app.db.visits().all().first()
        app.db.visits().update(visit.copy(hidden = true))
        model.addSuggestedPlace(suggestion)
        await { !model.uiState.value.placeBusy }
        assertTrue(app.placeRepository.all().isEmpty())
        assertNotNull(model.uiState.value.placeError)
    }
}
