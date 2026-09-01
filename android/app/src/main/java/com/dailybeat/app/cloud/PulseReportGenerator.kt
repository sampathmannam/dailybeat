package com.dailybeat.app.cloud

import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys

class PulseReportGenerator(
    private val settingsRepository: SettingsRepository,
    private val cloudLlm: CloudLlmClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
) {

    suspend fun generateAndSavePulse(): Result<String> {
        val date = DateKeys.today()
        val settings = settingsRepository.get()
        val visits = visitRepository.visitsForDate(date)
        val events = eventRepository.eventsForDate(date)

        if (visits.isEmpty() && events.isEmpty()) {
            return Result.failure(IllegalStateException("No activity yet for midday pulse."))
        }

        val context = ContextLimiter.trimForLlm(
            DayContextBuilder.build(
                date = date,
                officerName = settings.officerName,
                visits = visits,
                events = events,
            ),
        )

        val prompt = """
            Write a brief midday status pulse (3–5 sentences) for an IPS officer.
            Summarize where they have been so far today and key calls/notes.
            Formal tone. No invented facts.

            DATA:
            $context
        """.trimIndent()

        return cloudLlm.generate(
            settings,
            DayContextBuilder.SYSTEM_PROMPT,
            prompt,
            maxOutputTokens = CloudTokenBudgets.MIDDAY_PULSE,
        ).map { pulse ->
            val header = "— Midday pulse ${date} —\n"
            val block = header + pulse.trim()
            val existing = diaryRepository.textForDate(date).orEmpty()
            val merged = if (existing.isBlank()) block else "$existing\n\n$block"
            diaryRepository.saveForDate(date, merged)
            block
        }
    }
}
