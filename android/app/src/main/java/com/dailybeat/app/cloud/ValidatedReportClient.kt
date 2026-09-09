package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings

class ReportIntegrityException(message: String) : IllegalStateException(message)

class ValidatedReportClient(
    private val cloud: CloudTextGenerator,
) {
    suspend fun generate(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        source: DayContextBuilder.BuiltContext,
    ): Result<String> {
        val first = cloud.generate(
            settings = settings,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxOutputTokens = CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val firstCheck = ReportIntegrityValidator.validate(
            report = first,
            visitRefCount = source.visitRefCount,
            eventRefCount = source.eventRefCount,
        )
        if (firstCheck.isValid) return Result.success(first)

        val correction = ReportIntegrityValidator.correctionPrompt(
            originalPrompt = userPrompt,
            invalidReport = first,
            violations = firstCheck.violations,
        )
        val second = cloud.generate(
            settings = settings,
            systemPrompt = systemPrompt,
            userPrompt = correction,
            maxOutputTokens = CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val secondCheck = ReportIntegrityValidator.validate(
            report = second,
            visitRefCount = source.visitRefCount,
            eventRefCount = source.eventRefCount,
        )
        return if (secondCheck.isValid) {
            Result.success(second)
        } else {
            Result.failure(
                ReportIntegrityException(
                    "Cloud report failed source-integrity validation: " +
                        secondCheck.violations.joinToString(" "),
                ),
            )
        }
    }
}
