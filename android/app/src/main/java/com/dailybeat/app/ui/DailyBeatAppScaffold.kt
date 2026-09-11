package com.dailybeat.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailybeat.app.R
import com.dailybeat.app.ui.diary.DiaryScreen
import com.dailybeat.app.ui.feed.FeedScreen
import com.dailybeat.app.ui.insights.InsightsScreen
import com.dailybeat.app.ui.map.JourneyMapScreen
import com.dailybeat.app.ui.review.ReviewDayScreen
import com.dailybeat.app.ui.settings.SettingsScreen
import com.dailybeat.app.ui.today.TodayScreen
import com.dailybeat.app.ui.today.TodayViewModel
import java.time.LocalDate
import androidx.compose.ui.text.style.TextOverflow
import com.dailybeat.app.util.Formatters

object Routes {
    const val TODAY = "today"
    const val MAP = "journey-map/{dateKey}"
    const val DIARY = "diary/{dateKey}"
    const val REVIEW = "review/{dateKey}"
    const val DAYS = "days"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"

    fun diary(dateKey: String = "today"): String = "diary/$dateKey"
    fun map(dateKey: String = "today"): String = "journey-map/$dateKey"
    fun review(dateKey: String = "today"): String = "review/$dateKey"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBeatAppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.TODAY
    val todayLabel = Formatters.dayHeading(LocalDate.now())

    val todayViewModel: TodayViewModel = viewModel()
    val context = LocalContext.current
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            todayViewModel.recordVoiceNote()
        } else {
            todayViewModel.onVoicePermissionDenied()
        }
    }

    fun startVoiceCapture() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            todayViewModel.recordVoiceNote()
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != Routes.MAP && currentRoute != Routes.REVIEW) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("nav_today"),
                    selected = currentRoute == Routes.TODAY,
                    onClick = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                // Today is the start destination. Restoring the stack saved while
                                // a nested Diary was open can put the user straight back in Diary.
                                // Clearing detail state makes this tab an unambiguous home action.
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.TODAY) Icons.Filled.Today else Icons.Outlined.Today,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_today), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = colors,
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("nav_days"),
                    selected = currentRoute == Routes.DAYS || currentRoute == Routes.DIARY,
                    onClick = {
                        navController.navigate(Routes.DAYS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.DAYS || currentRoute == Routes.DIARY) {
                                Icons.Filled.DateRange
                            } else {
                                Icons.Outlined.DateRange
                            },
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_days), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = colors,
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("nav_insights"),
                    selected = currentRoute == Routes.INSIGHTS,
                    onClick = {
                        navController.navigate(Routes.INSIGHTS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (currentRoute == Routes.INSIGHTS) Icons.Filled.Insights else Icons.Outlined.Insights,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_insights), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = colors,
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("nav_settings"),
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
                    label = { Text(stringResource(R.string.tab_settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    viewModel = todayViewModel,
                    onRecordVoice = ::startVoiceCapture,
                    onOpenDiary = {
                        navController.navigate(Routes.diary()) {
                            launchSingleTop = true
                        }
                    },
                    onOpenMap = {
                        navController.navigate(Routes.map()) {
                            launchSingleTop = true
                        }
                    },
                    onReviewDay = {
                        navController.navigate(Routes.review()) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Routes.MAP,
                arguments = listOf(navArgument("dateKey") { type = NavType.StringType }),
            ) {
                JourneyMapScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.REVIEW,
                arguments = listOf(navArgument("dateKey") { type = NavType.StringType }),
            ) { entry ->
                val dateKey = entry.arguments?.getString("dateKey") ?: "today"
                ReviewDayScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMap = { navController.navigate(Routes.map(dateKey)) },
                    onOpenDiary = { navController.navigate(Routes.diary(it)) },
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
            composable(Routes.DAYS) {
                FeedScreen(
                    onOpenDay = { dateKey ->
                        // Tapping a day opens its map to look at, not the rename/hide/complete
                        // review flow. Review stays reachable from Today's "Review my day".
                        navController.navigate(Routes.map(dateKey)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onOpenToday = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenDay = { dateKey -> navController.navigate(Routes.review(dateKey)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
