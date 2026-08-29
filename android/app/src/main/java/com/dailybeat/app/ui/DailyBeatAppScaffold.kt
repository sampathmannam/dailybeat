package com.dailybeat.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailybeat.app.R
import com.dailybeat.app.ui.diary.DiaryScreen
import com.dailybeat.app.ui.history.HistoryScreen
import com.dailybeat.app.ui.settings.SettingsScreen
import com.dailybeat.app.ui.today.TodayScreen
import com.dailybeat.app.ui.today.TodayViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Routes {
    const val TODAY = "today"
    const val DIARY = "diary/{dateKey}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun diary(dateKey: String = "today"): String = "diary/$dateKey"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBeatAppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.TODAY
    val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    val todayViewModel: TodayViewModel = viewModel()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (currentRoute == Routes.TODAY) {
                FloatingActionButton(
                    onClick = todayViewModel::recordVoiceNote,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_fab_label))
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.TODAY,
                    onClick = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.TODAY) Icons.Filled.Today else Icons.Outlined.Today,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_today)) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = currentRoute.startsWith("diary/"),
                    onClick = {
                        navController.navigate(Routes.diary()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute.startsWith("diary/")) Icons.Filled.Book else Icons.Outlined.Book,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_diary)) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.HISTORY,
                    onClick = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_history)) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.SETTINGS,
                    onClick = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    colors = colors,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    headerSubtitle = todayLabel,
                    onOpenDiary = {
                        navController.navigate(Routes.diary()) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Routes.DIARY,
                arguments = listOf(
                    navArgument("dateKey") {
                        type = NavType.StringType
                        defaultValue = "today"
                    },
                ),
            ) {
                DiaryScreen()
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenDiary = { dateKey ->
                        navController.navigate(Routes.diary(dateKey)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
