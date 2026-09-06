package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryClearingTest {

    private lateinit var db: DailyBeatDb
    private lateinit var repository: DiaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DiaryRepository(db.diaries())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `clearing a diary sticks instead of resurrecting the old text`() = runBlocking {
        val today = DateKeys.today()
        repository.saveForDate(today, "Text the officer decided to remove.")

        repository.saveForDate(today, "")

        assertEquals(
            "The cleared diary came back, so the officer's deletion was silently discarded.",
            "",
            repository.textForDate(today),
        )
    }

    @Test
    fun `clearing does not leave a diary that counts as written`() = runBlocking {
        val today = DateKeys.today()
        repository.saveForDate(today, "Something")
        repository.saveForDate(today, "   ")

        assertEquals(0, repository.countNonEmpty())
    }
}
