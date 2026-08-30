package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedReportClientTest {

    @Test
    fun validFirstResponseReturnsWithoutCorrection() = runBlocking {
        val cloud = FakeCloud(
            ArrayDeque(listOf(Result.success("  Recorded event [E1].  "))),
        )

        val result = ValidatedReportClient(cloud).generate(
            AppSettings(),
            "system",
            "DATA: [E1] note",
            DayContextBuilder.BuiltContext("", 0, 1),
        )

        assertEquals("Recorded event [E1].", result.getOrThrow())
        assertEquals(listOf("DATA: [E1] note"), cloud.prompts)
    }

    @Test
    fun invalidFirstResponseIsCorrectedOnceAndValidSecondResponseWins() = runBlocking {
        val cloud = FakeCloud(
            ArrayDeque(
                listOf(
                    Result.success("Invented visit [V1]."),
                    Result.success("Recorded event [E1]."),
                ),
            ),
        )

        val result = ValidatedReportClient(cloud).generate(
            AppSettings(),
            "system",
            "DATA: [E1] note",
            DayContextBuilder.BuiltContext("", 0, 1),
        )

        assertEquals("Recorded event [E1].", result.getOrThrow())
        assertEquals(2, cloud.prompts.size)
        assertTrue(cloud.prompts.last().contains("Unknown citation [V1]."))
    }

    @Test
    fun repeatedInvalidResponseStopsAfterTwoCalls() = runBlocking {
        val cloud = FakeCloud(
            ArrayDeque(List(2) { Result.success("Invented [V1].") }),
        )

        val error = ValidatedReportClient(cloud).generate(
            AppSettings(),
            "system",
            "DATA: [E1] note",
            DayContextBuilder.BuiltContext("", 0, 1),
        ).exceptionOrNull()

        assertTrue(error is ReportIntegrityException)
        assertEquals(2, cloud.prompts.size)
    }

    @Test
    fun cloudFailureIsReturnedWithoutCorrection() = runBlocking {
        val cloudError = CloudRequestException(
            provider = "Fake",
            statusCode = 503,
            retryable = true,
            safeMessage = "Fake request failed.",
        )
        val cloud = FakeCloud(ArrayDeque(listOf(Result.failure(cloudError))))

        val error = ValidatedReportClient(cloud).generate(
            AppSettings(),
            "system",
            "DATA: [E1] note",
            DayContextBuilder.BuiltContext("", 0, 1),
        ).exceptionOrNull()

        assertSame(cloudError, error)
        assertEquals(1, cloud.prompts.size)
    }

    private class FakeCloud(
        private val replies: ArrayDeque<Result<String>>,
    ) : CloudTextGenerator {
        val prompts = mutableListOf<String>()

        override suspend fun generate(
            settings: AppSettings,
            systemPrompt: String,
            userPrompt: String,
            maxOutputTokens: Int,
        ): Result<String> {
            assertEquals(CloudTokenBudgets.DAILY_DIARY, maxOutputTokens)
            prompts += userPrompt
            return replies.removeFirst()
        }
    }
}
