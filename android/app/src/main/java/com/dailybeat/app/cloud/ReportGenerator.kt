package com.dailybeat.app.cloud

import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.llm.DairyGenerator
import java.time.LocalDate

class ReportGenerator(
    private val settingsRepository: SettingsRepository,
    private val cloudLlm: CloudLlmClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
    private val localGenerator: DairyGenerator,
) {

    suspend fun generateForToday(): Result<String> = generateForDate(LocalDate.now())

    suspend fun generateForDate(date: LocalDate): Result<String> {
        val settings = settingsRepository.get()
        val visits = visitRepository.visitsForDate(date)
        val events = eventRepository.eventsForDate(date)

        if (visits.isEmpty() && events.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "No passive data yet. Keep GPS on and move between places, or enable call log capture.",
                ),
            )
        }

        val context = DayContextBuilder.build(
            date = date,
            officerName = settings.officerName,
            visits = visits,
            events = events,
        )

        if (settingsRepository.isCloudBrainReady()) {
            val userPrompt = """
                Generate today's official daily diary from this passive activity log.
                End with a one-line summary of the day.

                DATA:
                $context
            """.trimIndent()

            return cloudLlm.generate(settings, DayContextBuilder.SYSTEM_PROMPT, userPrompt).map { report ->
                report.trim()
            }
        }

        return localGenerator.generateForDay(date)
    }

    suspend fun generateAndSaveForDate(date: LocalDate): Result<String> {
        return generateForDate(date).onSuccess { text ->
            diaryRepository.saveForDate(date, text)
        }
    }
}
