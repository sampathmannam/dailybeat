package com.dailybeat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dailybeat.app.ui.DailyBeatAppScaffold
import com.dailybeat.app.ui.theme.DailyBeatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailyBeatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DailyBeatAppScaffold()
                }
            }
        }
    }
}
