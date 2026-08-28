package com.dailybeat.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.Event
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DailyBeatApp).eventRepository

    val todayEvents = repository.observeTodayEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addManualEvent(text: String) {
        viewModelScope.launch {
            repository.addManualEvent(text)
        }
    }

    suspend fun todayEventsText(): String = repository.todayEventsText()
}
