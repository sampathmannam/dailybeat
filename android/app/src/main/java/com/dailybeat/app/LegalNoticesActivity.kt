package com.dailybeat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailybeat.app.data.settings.ThemePreference
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.ui.theme.DailyBeatTheme

class LegalNoticesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DailyBeatApp
        val notice = listOf(R.raw.third_party_notices, R.raw.gpl_3_0, R.raw.apache_license_2_0)
            .joinToString("\n\n") { resourceId ->
                resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
            }
        setContent {
            val preference by app.settingsRepository.themePreference.collectAsStateWithLifecycle()
            val dark = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            DailyBeatTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().readableContentWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            DailyBeatScreenHeader(
                                title = getString(R.string.legal_notices_title),
                                subtitle = getString(R.string.legal_notices_subtitle),
                            )
                        }
                        item {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
