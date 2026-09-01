package com.dailybeat.app.cloud

import android.content.Context
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import java.time.LocalDate

class ReportGenerator(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val validatedReportClient: ValidatedReportClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
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

        val source = DayContextBuilder.buildDetailed(
            date = date,
            officerName = settings.officerName,
            visits = visits,
            events = events,
        )
        val limitedContext = ContextLimiter.trimForLlm(source.text)

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
            $limitedContext
        """.trimIndent()

        return validatedReportClient.generate(
            settings,
            DayContextBuilder.SYSTEM_PROMPT,
            userPrompt,
            source,
        ).onFailure { error -> recordDailyReportFailure(context, error) }
    }

    suspend fun generateAndSaveForDate(date: LocalDate): Result<String> {
        return generateForDate(date).onSuccess { text ->
            diaryRepository.saveForDate(date, text)
        }
    }
}

internal fun recordDailyReportFailure(context: Context, error: Throwable) {
    val integrityFailure = error is ReportIntegrityException
    OperationalFailureLog.record(
        context = context,
        category = if (integrityFailure) "daily-report-integrity" else "daily-report",
        retryable = if (integrityFailure) false else ReportRetryPolicy.shouldRetry(error),
        message = when {
            integrityFailure -> "Daily report failed source-integrity validation."
            error is CloudRequestException -> error.message.orEmpty()
            else -> "Daily report generation failed (${error.javaClass.simpleName})."
        },
    )
}
