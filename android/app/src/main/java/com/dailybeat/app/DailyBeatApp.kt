package com.dailybeat.app

import android.app.Application
import androidx.room.Room
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.llm.LlmEngine

class DailyBeatApp : Application() {

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db").build()
    }

    val llm: LlmEngine by lazy { LlmEngine(this) }

    val eventRepository: EventRepository by lazy {
        EventRepository(db.events())
    }
}
