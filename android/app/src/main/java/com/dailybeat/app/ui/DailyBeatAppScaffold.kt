package com.dailybeat.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailybeat.app.R
import com.dailybeat.app.ui.generate.GenerateScreen
import com.dailybeat.app.ui.home.HomeScreen

enum class DailyBeatTab {
    Today,
    Generate,
}

@Composable
fun DailyBeatAppScaffold() {
    var selectedTab by rememberSaveable { mutableStateOf(DailyBeatTab.Today) }
    var prefilledEvents by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == DailyBeatTab.Today,
                    onClick = { selectedTab = DailyBeatTab.Today },
                    icon = { Icon(Icons.Default.Today, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_today)) },
                )
                NavigationBarItem(
                    selected = selectedTab == DailyBeatTab.Generate,
                    onClick = { selectedTab = DailyBeatTab.Generate },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_generate)) },
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            DailyBeatTab.Today -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                onGenerateFromToday = { events ->
                    prefilledEvents = events
                    selectedTab = DailyBeatTab.Generate
                },
            )
            DailyBeatTab.Generate -> GenerateScreen(
                modifier = Modifier.padding(innerPadding),
                prefilledEvents = prefilledEvents,
            )
        }
    }
}
