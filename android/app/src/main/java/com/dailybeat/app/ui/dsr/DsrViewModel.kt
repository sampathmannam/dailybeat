package com.dailybeat.app.ui.dsr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import com.dailybeat.app.dsr.DsrImportOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DsrDashboardData(
    val latestDsr: DsrImport? = null,
    val cases: List<DsrCase> = emptyList(),
    val stations: List<DsrStationSnapshot> = emptyList(),
    val forecasts: List<DsrForecast> = emptyList(),
    val issues: List<DsrQualityIssue> = emptyList(),
)

data class DsrOperationState(
    val importing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class DsrUiState(
    val dashboard: DsrDashboardData = DsrDashboardData(),
    val metrics: List<DsrMetricSnapshot> = emptyList(),
    val imports: List<DsrImport> = emptyList(),
    val operation: DsrOperationState = DsrOperationState(),
)

class DsrViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    private val operation = MutableStateFlow(DsrOperationState())

    private val dashboard = combine(
        app.dsrRepository.observeLatestDsr(),
        app.dsrRepository.observeLatestCases(),
        app.dsrRepository.observeLatestStations(),
        app.dsrRepository.observeLatestForecasts(),
        app.dsrRepository.observeLatestIssues(),
    ) { latest, cases, stations, forecasts, issues ->
        DsrDashboardData(latest, cases, stations, forecasts, issues)
    }

    val uiState: StateFlow<DsrUiState> = combine(
        dashboard,
        app.dsrRepository.observeActiveMetrics(),
        app.dsrRepository.observeRecentImports(),
        operation,
    ) { dashboardData, metrics, imports, operationState ->
        DsrUiState(dashboardData, metrics, imports, operationState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DsrUiState())

    fun importPdf(uri: Uri) {
        if (operation.value.importing) return
        viewModelScope.launch {
            operation.value = DsrOperationState(importing = true)
            runCatching { app.dsrImportService.import(uri) }.fold(
                onSuccess = { outcome ->
                    operation.value = when (outcome) {
                        is DsrImportOutcome.AlreadyImported -> DsrOperationState(
                            message = "Already imported: ${outcome.originalFileName}",
                        )
                        is DsrImportOutcome.Imported -> {
                            val replacement = if (outcome.replacedPreviousVersion) " Previous version archived." else ""
                            DsrOperationState(
                                message = "${outcome.reportType.name.replace('_', ' ')} imported: " +
                                    "${outcome.caseCount} cases, ${outcome.issueCount} checks.$replacement",
                            )
                        }
                    }
                },
                onFailure = { error ->
                    operation.value = DsrOperationState(
                        error = error.message ?: "The PDF could not be imported.",
                    )
                },
            )
        }
    }
}
