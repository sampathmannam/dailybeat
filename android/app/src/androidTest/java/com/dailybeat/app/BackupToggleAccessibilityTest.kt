package com.dailybeat.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.settings.LegacyBackupRestoreToggle
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupToggleAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun labelledCheckboxWorksAtTwoHundredPercentWithoutALiveBackupAccount() {
        requireDisposableTestApp()
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DailyBeatTheme {
                    Box(Modifier.width(320.dp)) {
                        LegacyBackupRestoreToggle(checked.value, enabled.value) { checked.value = it }
                    }
                }
            }
        }
        val label = compose.activity.getString(R.string.backup_legacy_restore)
        val node = compose.onNodeWithTag("legacy_backup_restore")
        node.assertTextContains(label).assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertHeightIsAtLeast(48.dp).performClick().assertIsOn()
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.runOnUiThread { enabled.value = false }
        node.assertIsNotEnabled().assertIsOn()
    }
}
