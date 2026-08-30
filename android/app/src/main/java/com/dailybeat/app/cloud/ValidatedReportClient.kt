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
            settings,
            systemPrompt,
            userPrompt,
            CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val firstCheck = ReportIntegrityValidator.validate(
            first,
            source.visitRefCount,
            source.eventRefCount,
        )
        if (firstCheck.isValid) return Result.success(first)

        val correction = ReportIntegrityValidator.correctionPrompt(
            userPrompt,
            first,
            firstCheck.violations,
        )
        val second = cloud.generate(
            settings,
            systemPrompt,
            correction,
            CloudTokenBudgets.DAILY_DIARY,
        ).getOrElse { return Result.failure(it) }.trim()
        val secondCheck = ReportIntegrityValidator.validate(
            second,
            source.visitRefCount,
            source.eventRefCount,
        )
        return if (secondCheck.isValid) {
            Result.success(second)
        } else {
            Result.failure(
                ReportIntegrityException(
                    "Cloud report failed source-integrity validation: ${secondCheck.violations.joinToString(" ")}",
                ),
            )
        }
    }
}
