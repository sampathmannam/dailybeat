package com.dailybeat.app.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local delivery for capture failures while PatrolGrid is already visible.
 * The same message is persisted by [com.dailybeat.app.data.settings.SettingsRepository]
 * so a process restart cannot hide the failure.
 */
object PatrolCaptureStatus {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun report(message: String) {
        _error.value = message
    }

    fun clear() {
        _error.value = null
    }
}

/**
 * Process-local delivery for non-capture evidence incidents. The retention manager also
 * persists the same privacy-safe message and aggregate incident metadata in settings.
 */
object PatrolEvidenceIncidentStatus {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun report(message: String) {
        _error.value = message
    }
}
