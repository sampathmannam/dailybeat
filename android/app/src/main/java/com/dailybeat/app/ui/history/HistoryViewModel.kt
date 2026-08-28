package com.dailybeat.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    val recentDiaries: StateFlow<List<DiaryEntry>> = app.diaryRepository.observeRecent(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
