package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.JournalProfile
import java.time.LocalDate

/** A factual chronology, available offline. It never infers an activity from a GPS visit. */
object LocalDiaryBuilder {
    fun fromContext(date: LocalDate, profile: JournalProfile, context: String): String = buildString {
        appendLine("${profile.documentTitle} — $date")
        appendLine("Draft · Review recorded times, places and notes before sharing.")
        appendLine()
        context.lineSequence().filter { it.matches(Regex("^\\[[VE]\\d+].*")) }
            .forEach { appendLine(it) }
        appendLine()
        append("Locations are estimates. Activities are recorded only when supplied in your notes.")
    }
}
