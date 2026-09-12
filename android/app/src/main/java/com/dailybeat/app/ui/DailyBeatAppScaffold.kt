package com.dailybeat.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.navigation.NavHostController
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

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val testTag: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.TODAY, R.string.tab_today, "nav_today", Icons.Filled.Today, Icons.Outlined.Today),
    TopLevelDestination(Routes.DAYS, R.string.tab_days, "nav_days", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    TopLevelDestination(Routes.INSIGHTS, R.string.tab_insights, "nav_insights", Icons.Filled.Insights, Icons.Outlined.Insights),
    TopLevelDestination(Routes.SETTINGS, R.string.tab_settings, "nav_settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private fun TopLevelDestination.isSelected(currentRoute: String): Boolean = when (route) {
    Routes.DAYS -> currentRoute == Routes.DAYS || currentRoute == Routes.DIARY
    else -> currentRoute == route
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

    fun navigateTo(destination: TopLevelDestination) {
        val isToday = destination.route == Routes.TODAY
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                // Today is an unambiguous home action: never restore a nested Diary.
                saveState = !isToday
            }
            launchSingleTop = true
            restoreState = !isToday
        }
    }

    val showTopLevelNavigation = currentRoute != Routes.MAP && currentRoute != Routes.REVIEW

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = showTopLevelNavigation && maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showTopLevelNavigation && !useNavigationRail) {
                    DailyBeatNavigationBar(
                        currentRoute = currentRoute,
                        onDestinationSelected = ::navigateTo,
                    )
                }
            },
        ) { innerPadding ->
            if (useNavigationRail) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    DailyBeatNavigationRail(
                        currentRoute = currentRoute,
                        onDestinationSelected = ::navigateTo,
                    )
                    DailyBeatNavHost(
                        navController = navController,
                        todayViewModel = todayViewModel,
                        todayLabel = todayLabel,
                        onStartVoiceCapture = ::startVoiceCapture,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                DailyBeatNavHost(
                    navController = navController,
                    todayViewModel = todayViewModel,
                    todayLabel = todayLabel,
                    onStartVoiceCapture = ::startVoiceCapture,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun DailyBeatNavigationBar(
    currentRoute: String,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        )
        topLevelDestinations.forEach { destination ->
            val selected = destination.isSelected(currentRoute)
            NavigationBarItem(
                modifier = Modifier.testTag(destination.testTag),
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = colors,
            )
        }
    }
}

@Composable
private fun DailyBeatNavigationRail(
    currentRoute: String,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().testTag("navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(12.dp))
        val colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        )
        topLevelDestinations.forEach { destination ->
            val selected = destination.isSelected(currentRoute)
            NavigationRailItem(
                modifier = Modifier.testTag(destination.testTag),
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = colors,
            )
        }
    }
}

@Composable
private fun DailyBeatNavHost(
    navController: NavHostController,
    todayViewModel: TodayViewModel,
    todayLabel: String,
    onStartVoiceCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        modifier = modifier.fillMaxSize(),
    ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    headerSubtitle = todayLabel,
                    viewModel = todayViewModel,
                    onRecordVoice = onStartVoiceCapture,
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
