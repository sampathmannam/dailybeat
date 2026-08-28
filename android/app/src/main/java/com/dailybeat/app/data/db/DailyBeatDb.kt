package com.dailybeat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.Place

@Database(entities = [Event::class, Place::class], version = 1, exportSchema = false)
abstract class DailyBeatDb : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun places(): PlaceDao
}
