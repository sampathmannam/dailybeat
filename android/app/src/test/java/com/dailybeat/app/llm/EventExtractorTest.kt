package com.dailybeat.app.llm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventExtractorTest {

    private val extractor = EventExtractor(LlmEngine(ApplicationProvider.getApplicationContext()))

    @Test
    fun extract_usesRegexFallbackWhenNoModel() = runBlocking {
        val result = extractor.extract(
            "Eleven forty, Market Beat, met IO Rajan, inspected chain snatching case FIR 247/26",
        )
        assertEquals("Market Beat", result.placeName)
        assertNotNull(result.caseNumbers)
        assertNotNull(result.peopleMentioned)
    }
}
