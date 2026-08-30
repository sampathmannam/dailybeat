package com.dailybeat.app.cloud

import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate

class WeeklyReportGenerator(
    private val settingsRepository: SettingsRepository,
    private val cloudLlm: CloudLlmClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
) {

    suspend fun generateAndSave(): Result<String> {
        if (!settingsRepository.isCloudBrainReady()) {
            return Result.failure(IllegalStateException("Cloud AI not configured. Add API key in Settings."))
        }

        val end = DateKeys.today()
        val start = end.minusDays(6)
        val settings = settingsRepository.get()
        val sections = mutableListOf<String>()
        sections += "WEEKLY ROLLUP: ${DateKeys.format(start)} to ${DateKeys.format(end)}"
        sections += "OFFICER: ${settings.officerName}"

        var day = start
        while (!day.isAfter(end)) {
            val visits = visitRepository.visitsForDate(day)
            val events = eventRepository.eventsForDate(day)
            val diary = diaryRepository.textForDate(day)
            sections += "--- ${DateKeys.format(day)} ---"
            sections += "Visits: ${visits.size}, Events: ${events.size}"
            if (!diary.isNullOrBlank()) {
                sections += diary.take(500)
            } else if (visits.isNotEmpty() || events.isNotEmpty()) {
                sections += ContextLimiter.trimForLlm(
                    DayContextBuilder.build(day, settings.officerName, visits, events),
                ).take(800)
            } else {
                sections += "No captured activity."
            }
            day = day.plusDays(1)
        }

        val context = ContextLimiter.trimForLlm(sections.joinToString("\n"))
        val prompt = """
            Write a weekly IPS diary rollup covering the past 7 days.
            Highlight patterns: frequent locations, call volume, key notes.
            Cite source refs like [V1] when present in data. Formal tone.

            DATA:
            $context
        """.trimIndent()

        return cloudLlm.generate(
            settings,
            DayContextBuilder.SYSTEM_PROMPT,
            prompt,
            maxOutputTokens = CloudTokenBudgets.WEEKLY_ROLLUP,
        ).map { report ->
            val block = "— Weekly rollup (${DateKeys.format(start)} – ${DateKeys.format(end)}) —\n${report.trim()}"
            diaryRepository.saveForDate(end, block)
            block
        }
    }
}
