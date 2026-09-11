package com.dailybeat.app.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class JourneyMapViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    // Exposed so the screen can title a past day honestly instead of always saying "Today".
    val date = DateKeys.parseOrToday(savedStateHandle["dateKey"])

    val model = combine(
        app.visitRepository.observeForDate(date),
        app.breadcrumbRepository.observeForDate(date),
        app.beatRepository.observe(date),
        app.placeRepository.observeAll(),
        app.diaryRepository.observeForDate(date),
    ) { visits, breadcrumbs, review, places, diary ->
        DayFeedBuilder.build(date, visits, diary?.text, places, breadcrumbs, review)
    }.map { JourneyMapModel.fromRoute(it.route) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            JourneyMapModel.fromPoints(emptyList()),
        )
}
