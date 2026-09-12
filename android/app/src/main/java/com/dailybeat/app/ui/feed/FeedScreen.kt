package com.dailybeat.app.ui.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate
import java.io.File
import com.dailybeat.app.util.Formatters

@Composable
fun FeedScreen(
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stayBeingNamed by remember { mutableStateOf<DayStay?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    // A destination can stay composed while the app is in the background. Refresh on resume
    // as well as navigation re-entry so newly captured visits do not leave the Feed stale.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.exportPath) {
        val path = state.exportPath ?: return@LaunchedEffect
        viewModel.consumeExport()
        runCatching {
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share DailyBeat export"))
        }.onFailure {
            viewModel.onExportShareFailed()
        }
    }

    stayBeingNamed?.let { stay ->
        NamePlaceDialog(
            stay = stay,
            onDismiss = { stayBeingNamed = null },
            onSave = { name ->
                viewModel.saveNamedPlace(stay, name)
                stayBeingNamed = null
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .readableContentWidth()
            .testTag("feed_list")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = stringResource(R.string.feed_title),
                subtitle = stringResource(R.string.feed_subtitle),
            )
        }

        item {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("feed_loading"))
            }
        }

        state.error?.let { error ->
            item { FeedErrorNotice(message = error, onRetry = viewModel::refresh) }
        }

        if (state.days.isEmpty() && !state.isLoading && state.error == null) {
            item {
                EmptyState(
                    title = stringResource(R.string.feed_empty_title),
                    subtitle = stringResource(R.string.feed_empty_subtitle),
                )
            }
        }

        items(state.days, key = { it.date.toString() }) { day ->
            DayFeedCard(
                day = day,
                onClick = { onOpenDay(DateKeys.format(day.date)) },
                onNameStay = { stay -> stayBeingNamed = stay },
            )
        }

        if (state.days.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.weekly_tools), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.weekly_tools_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.generate_weekly_rollup),
                            onClick = viewModel::generateWeeklyRollup,
                            enabled = !state.isGeneratingWeekly,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.export_week_package),
                            onClick = viewModel::exportPackage,
                            enabled = !state.isExporting,
                        )
                        if (state.isGeneratingWeekly || state.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                        state.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayFeedCard(
    day: DayFeedItem,
    onClick: () -> Unit,
    onNameStay: (DayStay) -> Unit,
) {
    // Reading LocalConfiguration makes this card recompose after a language/region change.
    // Do not cache the startup locale in a top-level formatter.
    val locale = LocalConfiguration.current.locales[0]
    // Lazy cards leave composition while scrolling. Preserve the officer's expansion choice
    // across scrolling, rotation and refreshed visits instead of silently collapsing the day.
    var showAllStays by rememberSaveable(day.date) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_card_${day.date}")
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = relativeDayLabel(day.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = Formatters.dayHeading(day.date, locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DayStateBadge(day.state)
            }

            if (day.hasRoute) {
                DayRouteThumbnail(
                    route = day.route,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    height = 136.dp,
                    contentDescription = pluralStringResource(
                        R.plurals.feed_route_content_description,
                        day.stayCount,
                        day.stayCount,
                    ),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = if (day.hasRoute) 14.dp else 0.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatBlock(
                    value = Formatters.distanceKm(day.distanceKm, day.distanceEstimated, locale),
                    label = stringResource(R.string.feed_stat_distance),
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.Start,
                )
                StatDivider()
                StatBlock(
                    value = Formatters.durationCompact(day.activeMinutes),
                    label = stringResource(R.string.feed_stat_time_out),
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.CenterHorizontally,
                )
                StatDivider()
                StatBlock(
                    value = Formatters.count(day.stayCount),
                    label = stringResource(R.string.feed_stat_stops),
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }

            if (day.stays.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val shownStays = if (showAllStays) day.stays else day.stays.take(MAX_STAYS_SHOWN)
                    shownStays.forEach { stay ->
                        StayRow(stay = stay, onNameStay = { onNameStay(stay) })
                    }
                    if (day.stays.size > MAX_STAYS_SHOWN) {
                        TextButton(
                            onClick = { showAllStays = !showAllStays },
                            modifier = Modifier.testTag("feed_toggle_stops_${day.date}"),
                        ) {
                            Text(
                                text = if (showAllStays) {
                                    stringResource(R.string.feed_show_fewer_stops)
                                } else {
                                    val hiddenCount = day.stays.size - MAX_STAYS_SHOWN
                                    pluralStringResource(
                                        R.plurals.feed_more_stops,
                                        hiddenCount,
                                        hiddenCount,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayStateBadge(state: String) {
    // Only the two states worth surfacing on Days get a badge: a genuine review prompt and a
    // completed Beat. Ordinary captured days — and today — carry no chip, so the list does not
    // tell the officer to review every day they ever had.
    val (label, color) = when (state) {
        "complete" -> stringResource(R.string.complete_label) to MaterialTheme.colorScheme.primaryContainer
        "needs_review" -> stringResource(R.string.needs_review_label) to MaterialTheme.colorScheme.tertiaryContainer
        else -> return
    }
    Surface(shape = CircleShape, color = color) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StayRow(stay: DayStay, onNameStay: () -> Unit) {
    val interactionModifier = if (stay.canBeNamed) {
        Modifier.clickable(onClick = onNameStay)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stay.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${Formatters.clock(stay.startMs)} · ${Formatters.duration(stay.durationMinutes)}")
                    if (!stay.canBeNamed) append(" · ${stringResource(R.string.feed_location_unreliable)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private const val MAX_STAYS_SHOWN = 5

@Composable
internal fun relativeDayLabel(date: LocalDate, today: LocalDate = DateKeys.today()): String =
    when (val relative = Formatters.relativeDay(date, today)) {
        Formatters.RelativeDay.Today -> stringResource(R.string.relative_today)
        Formatters.RelativeDay.Yesterday -> stringResource(R.string.relative_yesterday)
        is Formatters.RelativeDay.DaysAgo ->
            pluralStringResource(R.plurals.relative_days_ago, relative.days, relative.days)
        is Formatters.RelativeDay.OnDate -> Formatters.dayHeading(relative.date)
    }

@Composable
private fun FeedErrorNotice(message: String, onRetry: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("feed_error"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            onRetry?.let { retry ->
                TextButton(onClick = retry) { Text(stringResource(R.string.feed_load_retry)) }
            }
        }
    }
}

/**
 * Names the place a stay happened at. OpenStreetMap has no point of interest at many real stops,
 * so the map can only offer the road; naming it once makes every later stay there read correctly.
 */
@Composable
private fun NamePlaceDialog(
    stay: DayStay,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(stay) { mutableStateOf(stay.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_name_place_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.feed_name_place_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("name_place_field"),
                    singleLine = true,
                    label = { Text(stringResource(R.string.feed_name_place_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("name_place_save"),
            ) { Text(stringResource(R.string.feed_name_place_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_name_place_cancel)) }
        },
    )
}
