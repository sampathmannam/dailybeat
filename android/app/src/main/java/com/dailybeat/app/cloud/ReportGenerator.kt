package com.dailybeat.app.cloud

import android.content.Context
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate

class ReportGenerator(
    private val settingsRepository: SettingsRepository,
    private val cloudLlm: CloudLlmClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
    private val appContext: Context,
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

        val context = ContextLimiter.trimForLlm(
            DayContextBuilder.build(
                date = date,
                officerName = settings.officerName,
                visits = visits,
                events = events,
            ),
        )

        if (!settingsRepository.isCloudBrainReady()) {
            return Result.failure(
                IllegalStateException("DeepSeek is required. Enable Cloud AI and add a DeepSeek API key in Settings → Cloud AI."),
            )
        }

        val userPrompt = """
            Generate today's official daily diary from this passive activity log.
            Cite every fact with [V#] and [E#] refs from the DATA block.
            End with a one-line summary of the day.

            DATA:
            $context
        """.trimIndent()

        return cloudLlm.generate(settings, DayContextBuilder.SYSTEM_PROMPT, userPrompt).map { report ->
            report.trim()
        }.onFailure {
            ReportRetryWorker.enqueue(appContext, date)
        }
    }

    /** Explicit, officer-initiated generation: the freshly generated report becomes the diary. */
    suspend fun generateAndSaveForDate(date: LocalDate): Result<String> {
        return generateForDate(date).onSuccess { text ->
            diaryRepository.saveForDate(date, text)
        }
    }

    /**
     * Generation the officer did not ask for right now (the 8 PM alarm and its retries).
     * Nothing they typed by hand may be lost, so the report is kept in its own block and a
     * later run replaces that block instead of appending another copy.
     */
    suspend fun generateUnattendedForDate(date: LocalDate): Result<String> {
        return generateForDate(date).onSuccess { text ->
            val block = "$REPORT_MARKER${DateKeys.format(date)}) —\n$text"
            val existing = diaryRepository.textForDate(date).orEmpty()
            val base = existing.substringBefore(REPORT_MARKER).trimEnd()
            diaryRepository.saveForDate(date, if (base.isBlank()) block else "$base\n\n$block")
        }
    }

    private companion object {
        const val REPORT_MARKER = "— AI daily report ("
    }
}
