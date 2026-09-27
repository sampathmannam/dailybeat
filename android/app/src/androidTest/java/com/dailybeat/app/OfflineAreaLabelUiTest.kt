package com.dailybeat.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.ui.components.EventCard
import com.dailybeat.app.ui.theme.DailyBeatTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineAreaLabelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun approximateNamesAreReadableAtLargeTextInBothThemes() {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val dark = mutableStateOf(false)
        val labels = listOf(VisitLabels.approximateLocation(11.4557, 78.1856)!!,
            VisitLabels.approximateLocation(0.0, -140.0)!!)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme(darkTheme = dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            labels.forEach { label ->
                                EventCard(Event(timestamp = 0, type = "visit", rawText = "Stay · $label", placeName = label))
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            labels.forEachIndexed { index, label ->
                compose.onNodeWithText("Stay · $label").performScrollTo().assertIsDisplayed()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                try {
                    val directory = File(app.getExternalFilesDir(null), "offline-area-evidence").apply { mkdirs() }
                    File(directory, "large-${if (night) "dark" else "light"}-$index.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { bitmap.recycle() }
            }
        }
    }
}
