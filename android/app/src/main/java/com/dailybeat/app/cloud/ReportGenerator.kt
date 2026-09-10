package com.dailybeat.app.cloud

import android.content.Context
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate

class ReportGenerator(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val validatedReportClient: ValidatedReportClient,
    private val visitRepository: VisitRepository,
    private val eventRepository: EventRepository,
    private val diaryRepository: DiaryRepository,
    private val placeRepository: PlaceRepository,
) {

    suspend fun generateForDate(date: LocalDate): Result<String> {
        val settings = settingsRepository.get()
        val visits = visitRepository.visitsForDate(date)
        val events = eventRepository.eventsForDate(date)
        val places = placeRepository.all()

        if (visits.isEmpty() && events.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "No passive data yet. Keep capture on and move between places, or add a note.",
                ),
            )
        }

        val source = DayContextBuilder.buildDetailed(
            date = date,
            officerName = settings.officerName,
            visits = visits,
            events = events,
            places = places,
        )
        val limitedText = ContextLimiter.trimForLlm(source.text)
        // ContextLimiter keeps the start of the timeline, so only allow citations that survived
        // truncation. This avoids accepting a model citation to a source it never received.
        val limitedSource = source.copy(
            text = limitedText,
            visitRefCount = largestVisibleRef(limitedText, 'V'),
            eventRefCount = largestVisibleRef(limitedText, 'E'),
        )
        if (limitedSource.visitRefCount == 0 && limitedSource.eventRefCount == 0) {
            return Result.failure(
                IllegalStateException("No usable journey or note data exists for this day."),
            )
        }

        if (!settingsRepository.isCloudBrainReady()) {
            return Result.failure(
                IllegalStateException(
                    "Cloud AI is required. Enable it and add the provider API key in Settings → Cloud AI.",
                ),
            )
        }

        val userPrompt = """
            Generate today's official daily diary from this passive activity log.
            Cite every fact with [V#] and [E#] refs from the DATA block.
            End with a one-line summary of the day.

            DATA:
            ${limitedSource.text}
        """.trimIndent()

        return validatedReportClient.generate(
            settings = settings,
            systemPrompt = DayContextBuilder.SYSTEM_PROMPT,
            userPrompt = userPrompt,
            source = limitedSource,
        ).onFailure { error -> recordDailyReportFailure(appContext, error) }
    }

    /** Explicit, officer-initiated generation: the freshly generated report becomes the diary. */
    suspend fun generateAndSaveForDate(date: LocalDate): Result<String> {
        return generateForDate(date).mapCatching { text ->
            diaryRepository.saveForDate(date, text)
            text
        }
    }

    /**
     * Generation the officer did not ask for right now (the 8 PM alarm and its retries).
     * Nothing they typed by hand may be lost, so the report is kept in its own block and a
     * later run replaces that block instead of appending another copy.
     */
    suspend fun generateUnattendedForDate(date: LocalDate): Result<String> {
        return generateForDate(date).mapCatching { text ->
            val block = "$REPORT_START_BOUNDARY$REPORT_MARKER${DateKeys.format(date)}) —\n" +
                "$text\n$REPORT_END_BOUNDARY"
            val existing = diaryRepository.textForDate(date).orEmpty()
            diaryRepository.saveForDate(
                date,
                GeneratedDiaryBlock.merge(
                    existing,
                    REPORT_START_BOUNDARY,
                    REPORT_END_BOUNDARY,
                    block,
                ),
            )
            text
        }
    }

    private companion object {
        const val REPORT_MARKER = "— AI daily report ("
        const val REPORT_START_BOUNDARY = "\u2063\u2062\u2063"
        const val REPORT_END_BOUNDARY = "\u2063\u2064\u2063"

        fun largestVisibleRef(text: String, prefix: Char): Int =
            Regex("\\[$prefix(\\d+)\\]")
                .findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull()
                ?: 0
    }
}

internal fun recordDailyReportFailure(context: Context, error: Throwable) {
    val integrityFailure = error is ReportIntegrityException
    OperationalFailureLog.record(
        context = context,
        category = if (integrityFailure) "daily-report-integrity" else "daily-report",
        retryable = !integrityFailure && ReportRetryPolicy.shouldRetry(error),
        message = when {
            integrityFailure -> "Daily report failed source-integrity validation."
            error is CloudRequestException -> error.message.orEmpty()
            else -> "Daily report generation failed (${error.javaClass.simpleName})."
        },
    )
}
