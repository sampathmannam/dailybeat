package com.dailybeat.app.cloud

import com.dailybeat.app.data.settings.JournalProfile
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalDiaryBuilderTest {
    @Test fun offlineDraftIncludesOnlyRecordsAndDoesNotInventActivitiesFromLocation() {
        val text = LocalDiaryBuilder.fromContext(LocalDate.of(2026, 9, 15), JournalProfile.PERSONAL,
            "AUTHOR: Secret name\nTEMPLATE: personal\n[V1] 10:00–10:30: Court\n[E1] 11:00: User's note")
        assertTrue(text.contains("[V1]"))
        assertTrue(text.contains("Draft"))
        assertFalse(text.contains("Secret name"))
        assertFalse(text.contains("attended a hearing"))
        assertTrue(text.contains("Daily journal"))
    }
    @Test fun uncitedSentenceCannotHideBehindAnotherSentencesValidCitation() {
        assertFalse(ReportIntegrityValidator.validate(
            "Arrested a suspect. Recorded at Court [V1].", 1, 0).isValid)
        assertTrue(ReportIntegrityValidator.validate(
            "Overview\nRecorded at Court [V1].\nUser noted a follow-up [E1].", 1, 1).isValid)
    }
}
