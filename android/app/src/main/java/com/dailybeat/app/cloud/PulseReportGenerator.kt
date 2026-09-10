package com.dailybeat.app.cloud

import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys

class PulseReportGenerator(
    private val settingsRepository: SettingsRepository,
    private val cloudLlm: CloudLlmClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
    private val placeRepository: PlaceRepository,
) {

    suspend fun generateAndSavePulse(): Result<String> {
        val date = DateKeys.today()
        val settings = settingsRepository.get()
        val visits = visitRepository.visitsForDate(date)
        val events = eventRepository.eventsForDate(date)
        val places = placeRepository.all()

        if (visits.isEmpty() && events.isEmpty()) {
            return Result.failure(IllegalStateException("No activity yet for midday pulse."))
        }

        val context = ContextLimiter.trimForLlm(
            DayContextBuilder.build(
                date = date,
                officerName = settings.officerName,
                visits = visits,
                events = events,
                places = places,
            ),
        )

        val prompt = """
            Write a brief midday status pulse (3–5 sentences) for an IPS officer.
            Summarize where they have been so far today and key notes.
            Formal tone. No invented facts.

            DATA:
            $context
        """.trimIndent()

        return cloudLlm.generate(
            settings = settings,
            systemPrompt = DayContextBuilder.SYSTEM_PROMPT,
            userPrompt = prompt,
            maxOutputTokens = CloudTokenBudgets.MIDDAY_PULSE,
        ).mapCatching { pulse ->
            val block = "$PULSE_START_BOUNDARY$PULSE_MARKER${date} —\n" +
                "${pulse.trim()}\n$PULSE_END_BOUNDARY"
            val existing = diaryRepository.textForDate(date).orEmpty()
            val merged = GeneratedDiaryBlock.merge(
                existing = existing,
                startPrefix = PULSE_START_BOUNDARY,
                endMarker = PULSE_END_BOUNDARY,
                replacement = block,
            )
            diaryRepository.saveForDate(date, merged)
            block
        }
    }

    private companion object {
        const val PULSE_MARKER = "— Midday pulse "
        const val PULSE_START_BOUNDARY = "\u2063\u2062\u2062\u2062\u2063"
        const val PULSE_END_BOUNDARY = "\u2063\u2064\u2064\u2064\u2063"
    }
}
