package com.dailybeat.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateKeys {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun format(date: LocalDate): String = date.format(formatter)

    fun parseOrToday(key: String?): LocalDate {
        if (key.isNullOrBlank() || key == "today") return today()
        return try {
            LocalDate.parse(key, formatter)
        } catch (_: Exception) {
            today()
        }
    }
}
