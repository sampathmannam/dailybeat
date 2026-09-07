package com.dailybeat.app.ui.dsr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.SectionHeader
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DsrCommandScreen(
    modifier: Modifier = Modifier,
    viewModel: DsrViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPdf)
    }
    val latest = state.dashboard.latestDsr
    val total = state.dashboard.stations.firstOrNull { it.stationCode == "TOTAL" }
    val reported = total?.reportedCases ?: state.dashboard.cases.size
    val priorityCases = state.dashboard.cases.count { it.priority == "CRITICAL" || it.priority == "HIGH" }
    val mvCases = (total?.mvDdCases ?: 0) + (total?.mvOtherCases ?: 0)
    val crimePeriods = allCrimePeriods(state.metrics)
    val latestCrimePeriod = crimePeriods.maxByOrNull { it.reportDate }
    val latestCrimeStations = latestCrimePeriod?.let { allCrimeStations(state.metrics, it.importId) }.orEmpty()
    val latestCrimeHeads = latestCrimePeriod?.let { allCrimeHeads(state.metrics, it.importId) }.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = "DSR Command",
                subtitle = latest?.reportDate?.let { "Rasipuram subdivision · ${displayDate(it)}" }
                    ?: "Upload the daily DSR to create the command view.",
            )
        }

        item {
            Button(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                enabled = !state.operation.importing,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (state.operation.importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                }
                Text(
                    text = if (state.operation.importing) "Reading and validating PDF…" else "Upload DSR or operational PDF",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            state.operation.message?.let { StatusMessage(it, error = false) }
            state.operation.error?.let { StatusMessage(it, error = true) }
        }

        if (latest == null) {
            item {
                EmptyState(
                    title = "No DSR imported",
                    subtitle = "Select a PDF. Extraction happens on this device; names, phone numbers and narrative gists are not added to the dashboard.",
                )
            }
        } else {
            item {
                ImportHealthCard(latest)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricPill("Reported", reported.toString(), Modifier.weight(1f))
                    MetricPill("Priority", priorityCases.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricPill("Charged", (total?.chargedCases ?: 0).toString(), Modifier.weight(1f))
                    MetricPill("MV cases", mvCases.toString(), Modifier.weight(1f))
                }
            }

            pendingMetrics(state.metrics).takeIf { it.isNotEmpty() }?.let { pending ->
                item { SectionHeader("Pending work") }
                items(pending, key = { it.metricCode }) { metric -> PendingMetricRow(metric) }
            }

            state.dashboard.forecasts.takeIf { it.isNotEmpty() }?.let { forecasts ->
                item { SectionHeader("Advance forecast") }
                items(forecasts.take(5), key = { it.id }) { forecast -> ForecastCard(forecast) }
            }

            state.dashboard.cases.takeIf { it.isNotEmpty() }?.let { cases ->
                item { SectionHeader("Reported cases") }
                items(cases.take(20), key = { it.caseKey }) { case -> CaseCard(case) }
            }

            state.dashboard.stations.filter { it.stationCode != "TOTAL" }.takeIf { it.isNotEmpty() }?.let { stations ->
                item { SectionHeader("Station activity") }
                items(stations, key = { it.stationCode }) { station -> StationRow(station, reported.coerceAtLeast(1)) }
            }

        }

        crimePeriods.takeIf { it.isNotEmpty() }?.let { periods ->
            item { SectionHeader("All-crime trend") }
            item { AllCrimeTrendCard(periods) }
        }

        latestCrimeStations.takeIf { it.isNotEmpty() }?.let { stations ->
            item { SectionHeader("Station detection gaps") }
            items(stations.take(6), key = { it.code }) { station -> AllCrimeStationCard(station) }
        }

        latestCrimeHeads.takeIf { it.isNotEmpty() }?.let { heads ->
            item { SectionHeader("Crime-head load") }
            items(heads.take(5), key = { it.code }) { head -> AllCrimeHeadCard(head) }
        }

        supportingMetrics(state.metrics).takeIf { it.isNotEmpty() }?.let { supporting ->
            item { SectionHeader("Operational picture") }
            items(supporting, key = { it.metricCode }) { metric -> SupportingMetricRow(metric) }
        }

        state.dashboard.issues.takeIf { it.isNotEmpty() }?.let { issues ->
            item { SectionHeader("Needs verification") }
            items(issues.take(12), key = { it.id }) { issue -> QualityIssueCard(issue) }
        }

        state.imports.takeIf { it.isNotEmpty() }?.let { imports ->
            item { SectionHeader("Import history") }
            items(imports.take(10), key = { it.id }) { report -> ImportHistoryRow(report) }
        }
    }
}

@Composable
private fun ImportHealthCard(value: DsrImport) {
    val healthColor = when {
        value.qualityScore >= 90 -> Color(0xFF15803D)
        value.qualityScore >= 70 -> Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Latest import", style = MaterialTheme.typography.labelLarge)
                    Text(
                        value.originalFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${value.qualityScore}%", color = healthColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = value.qualityScore / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = healthColor,
            )
            Text(
                "${value.pageCount} pages · ${value.issueCount} verification checks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaseCard(value: DsrCase) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(value.head, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${value.stationName} · Cr. ${value.displayCrimeNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PriorityBadge(value.priority)
            }
            Text(value.lawSections, style = MaterialTheme.typography.bodyMedium)
            if (value.needsReview) {
                Text("Conflicting source row — verify before use", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ForecastCard(value: DsrForecast) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (value.priority == "HIGH") Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(value.stationName, style = MaterialTheme.typography.titleMedium)
                PriorityBadge(value.priority)
            }
            Text(
                value.category.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(value.details, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            value.expectedCrowd?.let {
                Text("Expected crowd: $it", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StationRow(value: DsrStationSnapshot, maxReported: Int) {
    val count = value.reportedCases ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(value.stationName, style = MaterialTheme.typography.titleSmall)
                Text("$count reported · ${value.chargedCases ?: 0} charged", style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = (count.toFloat() / maxReported).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PendingMetricRow(value: DsrMetricSnapshot) {
    val label = when (value.metricCode) {
        "esummon.pending" -> "e-Summons pending"
        "esakshya.pending" -> "e-Sakshya pending linkage"
        "esakshya.sid_later" -> "SID marked for later linkage"
        "nbw.pending" -> "NBWs pending"
        "efile_rectification.pending" -> "e-file rectifications pending"
        else -> value.metricCode
    }
    Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.metricValue.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AllCrimeTrendCard(values: List<AllCrimePeriod>) {
    val maxMonthlyRate = values.maxOfOrNull { it.reported.toFloat() / it.months.coerceAtLeast(1) }
        ?.coerceAtLeast(1f) ?: 1f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Bars compare cases per covered month, so YTD reports are not treated as full years.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            values.sortedBy { it.reportDate }.forEach { period ->
                val monthlyRate = period.reported.toFloat() / period.months.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(allCrimePeriodLabel(period), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${period.reported} reported",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = (monthlyRate / maxMonthlyRate).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${oneDecimal(monthlyRate)}/month · ${percentage(period.detected, period.reported)}% detected · " +
                            "${percentage(period.propertyRecovered, period.propertyLost)}% property recovered",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Property ₹${indianNumber(period.propertyLost)} lost · ₹${indianNumber(period.propertyRecovered)} recovered",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllCrimeStationCard(value: AllCrimeStation) {
    val rate = if (value.reported == 0) 0f else value.detected.toFloat() / value.reported
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(value.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${value.reported - value.detected} detection gap",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (value.reported > value.detected) Color(0xFFB45309) else Color(0xFF15803D),
                )
            }
            LinearProgressIndicator(progress = rate.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Text(
                "${value.detected}/${value.reported} detected · ₹${indianNumber(value.propertyRecovered)} of " +
                    "₹${indianNumber(value.propertyLost)} recovered",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllCrimeHeadCard(value: AllCrimeHead) {
    Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(allCrimeHeadLabel(value.code), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${value.reported} reported · ${value.detected} detected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${value.underInvestigation} UI",
                style = MaterialTheme.typography.labelLarge,
                color = if (value.underInvestigation > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupportingMetricRow(value: DsrMetricSnapshot) {
    val label = when (value.metricCode) {
        "tasmac.shops" -> "TASMAC shops"
        "tasmac.cctv_available" -> "TASMAC CCTV cameras"
        "tasmac.cctv_working" -> "TASMAC cameras working"
        "tasmac.patta_available" -> "Patta Books available"
        "tasmac.patta_missing" -> "Patta Books missing"
        "traffic.fatal_accident_cases" -> "Fatal-accident cases in report"
        else -> value.metricCode
    }
    Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(value.metricValue.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QualityIssueCard(value: DsrQualityIssue) {
    val color = if (value.severity == "ERROR") MaterialTheme.colorScheme.error else Color(0xFFB45309)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.08f),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).background(color, CircleShape)) {}
            Column {
                Text(value.code.replace('_', ' '), style = MaterialTheme.typography.labelLarge, color = color)
                Text(value.message, style = MaterialTheme.typography.bodyMedium)
                value.sourcePage?.let { Text("Source page $it", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(value: DsrImport) {
    Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(value.reportType.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(value.reportDate, "${value.pageCount} pages").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (value.active) "Active" else "Archived", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val color = when (priority) {
        "CRITICAL" -> MaterialTheme.colorScheme.error
        "HIGH" -> Color(0xFFB45309)
        "MEDIUM" -> Color(0xFF1D4ED8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.11f)) {
        Text(
            priority.lowercase().replaceFirstChar(Char::uppercase),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun StatusMessage(value: String, error: Boolean) {
    Text(
        value,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else Color(0xFF15803D),
    )
}

private fun pendingMetrics(values: List<DsrMetricSnapshot>): List<DsrMetricSnapshot> = values
    .filter { it.reportType == "DSR" && it.semantics == "PENDING" }
    .distinctBy { it.metricCode }

private fun supportingMetrics(values: List<DsrMetricSnapshot>): List<DsrMetricSnapshot> = values
    .filter { it.reportType == "TASMAC" || it.reportType == "FATAL_ACCIDENT" }
    .distinctBy { it.metricCode }

private data class AllCrimePeriod(
    val importId: String,
    val reportDate: String,
    val months: Int,
    val reported: Int,
    val detected: Int,
    val propertyLost: Int,
    val propertyRecovered: Int,
)

private data class AllCrimeStation(
    val code: String,
    val name: String,
    val reported: Int,
    val detected: Int,
    val propertyLost: Int,
    val propertyRecovered: Int,
)

private data class AllCrimeHead(
    val code: String,
    val reported: Int,
    val detected: Int,
    val underInvestigation: Int,
)

private fun allCrimePeriods(values: List<DsrMetricSnapshot>): List<AllCrimePeriod> = values
    .filter { it.reportType == "ALL_CRIME" }
    .groupBy { it.importId }
    .mapNotNull { (importId, snapshots) ->
        val byCode = snapshots.associateBy { it.metricCode }
        val reportDate = snapshots.firstNotNullOfOrNull { it.reportDate } ?: return@mapNotNull null
        val reported = byCode["allcrime.reported"]?.metricValue ?: return@mapNotNull null
        AllCrimePeriod(
            importId = importId,
            reportDate = reportDate,
            months = byCode["allcrime.period_months"]?.metricValue
                ?: runCatching { LocalDate.parse(reportDate).monthValue }.getOrDefault(12),
            reported = reported,
            detected = byCode["allcrime.detected"]?.metricValue ?: 0,
            propertyLost = byCode["allcrime.property_lost"]?.metricValue ?: 0,
            propertyRecovered = byCode["allcrime.property_recovered"]?.metricValue ?: 0,
        )
    }
    .sortedBy { it.reportDate }

private fun allCrimeStations(values: List<DsrMetricSnapshot>, importId: String): List<AllCrimeStation> {
    val prefix = "allcrime.station."
    return values.asSequence()
        .filter { it.importId == importId && it.metricCode.startsWith(prefix) }
        .groupBy {
            it.metricCode.removePrefix(prefix).substringBeforeLast('.')
        }
        .mapNotNull { (code, snapshots) ->
            if (code == "TOTAL") return@mapNotNull null
            val fields = snapshots.associate { it.metricCode.substringAfterLast('.') to it.metricValue }
            AllCrimeStation(
                code = code,
                name = allCrimeStationName(code),
                reported = fields["reported"] ?: return@mapNotNull null,
                detected = fields["detected"] ?: 0,
                propertyLost = fields["property_lost"] ?: 0,
                propertyRecovered = fields["property_recovered"] ?: 0,
            )
        }
        .sortedWith(compareByDescending<AllCrimeStation> { it.reported - it.detected }.thenByDescending { it.reported })
}

private fun allCrimeHeads(values: List<DsrMetricSnapshot>, importId: String): List<AllCrimeHead> {
    val prefix = "allcrime.head."
    return values.asSequence()
        .filter { it.importId == importId && it.metricCode.startsWith(prefix) }
        .groupBy { it.metricCode.removePrefix(prefix).substringBeforeLast('.') }
        .mapNotNull { (code, snapshots) ->
            if (code == "TOTAL") return@mapNotNull null
            val fields = snapshots.associate { it.metricCode.substringAfterLast('.') to it.metricValue }
            AllCrimeHead(
                code = code,
                reported = fields["reported"] ?: return@mapNotNull null,
                detected = fields["detected"] ?: 0,
                underInvestigation = fields["under_investigation"] ?: 0,
            )
        }
        .filter { it.reported > 0 }
        .sortedWith(compareByDescending<AllCrimeHead> { it.underInvestigation }.thenByDescending { it.reported })
}

private fun allCrimePeriodLabel(value: AllCrimePeriod): String {
    val date = runCatching { LocalDate.parse(value.reportDate) }.getOrNull() ?: return value.reportDate
    return if (value.months >= 12) {
        "${date.year} full year"
    } else {
        "Jan-${date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))} ${date.year} YTD"
    }
}

private fun allCrimeStationName(code: String): String = when (code) {
    "RASIPURAM" -> "Rasipuram"
    "VENNANDUR" -> "Vennandur"
    "NAMAGIRIPET" -> "Namagiripet"
    "BELUKURICHI" -> "Belukurichi"
    "AYILPATTY" -> "Ayilpatty"
    "MANGALAPURAM" -> "Mangalapuram"
    else -> code.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}

private fun allCrimeHeadLabel(code: String): String = when (code) {
    "MURDER_FOR_GAIN" -> "Murder for gain"
    "HB_DAY" -> "Housebreaking - day"
    "HB_NIGHT" -> "Housebreaking - night"
    "THEFT_A" -> "Theft - A"
    "THEFT_B" -> "Theft - B"
    "THEFT_C" -> "Theft - C"
    else -> code.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}

private fun percentage(numerator: Int, denominator: Int): Int =
    if (denominator == 0) 0 else (numerator * 100.0 / denominator).roundToInt()

private fun oneDecimal(value: Float): String = String.format(Locale.ENGLISH, "%.1f", value)

private fun indianNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(value)

private fun displayDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
}.getOrDefault(value)
