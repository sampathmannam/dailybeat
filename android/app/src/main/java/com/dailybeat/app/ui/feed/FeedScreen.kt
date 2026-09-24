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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
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
import com.dailybeat.app.ui.components.InlineFeedback
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate
import java.io.File
import com.dailybeat.app.util.Formatters
import com.dailybeat.app.util.InputPolicy
import com.dailybeat.app.domain.VisitLabels
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stayBeingNamed by remember(state.dataGeneration) { mutableStateOf<DayStay?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sharePreviews by remember(state.dataGeneration) {
        mutableStateOf<List<com.dailybeat.app.export.DiarySharePreview>?>(null)
    }
    sharePreviews?.let { previews ->
        com.dailybeat.app.ui.components.SharePreviewDialog(previews, state.isExporting,
            onDismiss = { sharePreviews = null },
            onConfirm = { viewModel.exportPackage(previews); sharePreviews = null })
    }

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
            isSaving = state.isSavingPlace,
            error = state.error,
            onDismiss = { if (!state.isSavingPlace) stayBeingNamed = null },
            onSave = { name ->
                viewModel.saveNamedPlace(stay, name) { stayBeingNamed = null }
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
            Column {
                Text("${state.throughDate.minusDays(29)} – ${state.throughDate}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val date = state.throughDate
                    android.app.DatePickerDialog(context, { _, year, month, day ->
                        viewModel.browseThrough(LocalDate.of(year, month + 1, day))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, modifier = Modifier.testTag("history_choose_date")) { Text("Go to date") }
            }
        }
        item {
            OutlinedTextField(value = state.searchQuery, onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth().testTag("history_search"), singleLine = true,
                label = { Text("Search notes, diaries and places") },
                supportingText = { Text("On this phone · Up to 100 matching records") })
        }
        if (state.searchQuery.isNotBlank()) {
            if (state.isSearching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (state.searchResults.isEmpty()) item { Text("No matching records. Try another word.") }
            items(state.searchResults) { hit ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    onOpenDay(hit.date().toString())
                }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${hit.kind} · ${hit.date()}", style = MaterialTheme.typography.titleSmall)
                    Text(hit.snippet, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
        item {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("feed_loading"))
            }
        }

        state.error?.let { error ->
            item {
                InlineFeedback(
                    message = error,
                    isError = true,
                    modifier = Modifier.testTag("feed_error"),
                    actionLabel = if (state.days.isEmpty()) {
                        stringResource(R.string.feed_load_retry)
                    } else {
                        null
                    },
                    onAction = if (state.days.isEmpty()) viewModel::refresh else null,
                )
            }
        }

        if (state.days.isEmpty() && !state.isLoading && state.error == null && state.searchQuery.isBlank()) {
            item {
                EmptyState(
                    title = stringResource(R.string.feed_empty_title),
                    subtitle = stringResource(R.string.feed_empty_subtitle),
                )
            }
        }

        items(if (state.searchQuery.isBlank()) state.days else emptyList(), key = { it.date.toString() }) { day ->
            DayFeedCard(
                day = day,
                onClick = { onOpenDay(DateKeys.format(day.date)) },
                onNameStay = { stay -> stayBeingNamed = stay },
            )
        }

        if (state.days.isNotEmpty() && state.searchQuery.isBlank()) {
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
                        val weeklyBusy = state.isGeneratingWeekly || state.isExporting
                        PrimaryButton(
                            text = stringResource(R.string.generate_weekly_rollup),
                            onClick = viewModel::generateWeeklyRollup,
                            enabled = !weeklyBusy,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.export_week_package),
                            onClick = { scope.launch { sharePreviews = viewModel.preparePackage() } },
                            enabled = !weeklyBusy,
                        )
                        if (state.isGeneratingWeekly || state.isExporting) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.weekly_working),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        state.message?.let {
                            InlineFeedback(message = it, isError = false)
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
    // Days stay scan-friendly by default. Preserve an explicit expansion across scrolling,
    // rotation and refreshed visits so the user does not have to reopen the same route details.
    var showRouteDetails by rememberSaveable(day.date) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_card_${day.date}")
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.feed_open_day_action),
                onClick = onClick,
            ),
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
                        modifier = Modifier.testTag("feed_date_${day.date}"),
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
                    label = stringResource(R.string.feed_stat_auto_stops),
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
                    if (showRouteDetails) {
                        Text(
                            text = stringResource(R.string.feed_where_you_went),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        day.stays.forEach { stay ->
                            StayRow(stay = stay, onNameStay = { onNameStay(stay) })
                        }
                    }
                    TextButton(
                        onClick = { showRouteDetails = !showRouteDetails },
                        modifier = Modifier.testTag("feed_toggle_stops_${day.date}"),
                    ) {
                        Text(
                            text = stringResource(
                                if (showRouteDetails) R.string.feed_less else R.string.feed_more,
                            ),
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
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.feed_name_place_action),
            onClick = onNameStay,
        )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
            if (stay.name == VisitLabels.UNAVAILABLE && stay.canBeNamed) {
                Text(
                    text = stringResource(R.string.feed_name_missing_place_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

@Composable
internal fun relativeDayLabel(date: LocalDate, today: LocalDate = DateKeys.today()): String =
    when (val relative = Formatters.relativeDay(date, today)) {
        Formatters.RelativeDay.Today -> stringResource(R.string.relative_today)
        Formatters.RelativeDay.Yesterday -> stringResource(R.string.relative_yesterday)
        is Formatters.RelativeDay.DaysAgo ->
            pluralStringResource(R.plurals.relative_days_ago, relative.days, relative.days)
        is Formatters.RelativeDay.OnDate -> Formatters.dayHeading(relative.date)
    }

/**
 * Names the place a stay happened at. OpenStreetMap has no point of interest at many real stops,
 * so the map can only offer the road; naming it once makes every later stay there read correctly.
 */
@Composable
private fun NamePlaceDialog(
    stay: DayStay,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(stay) {
        mutableStateOf(stay.name.takeUnless { it == VisitLabels.UNAVAILABLE }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                    onValueChange = {
                        draft = InputPolicy.singleLine(it, InputPolicy.PLACE_NAME_CHARS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("name_place_field"),
                    singleLine = true,
                    label = { Text(stringResource(R.string.feed_name_place_label)) },
                )
                error?.let { message ->
                    InlineFeedback(
                        message = message,
                        isError = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank() && !isSaving,
                modifier = Modifier.testTag("name_place_save"),
            ) { Text(stringResource(R.string.feed_name_place_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.feed_name_place_cancel))
            }
        },
    )
}
