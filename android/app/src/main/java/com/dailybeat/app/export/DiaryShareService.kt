package com.dailybeat.app.export

import com.dailybeat.app.cloud.DayContextBuilder
import com.dailybeat.app.cloud.LocalDiaryBuilder
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.JournalProfile
import com.dailybeat.app.data.settings.SettingsRepository
import java.security.MessageDigest
import java.time.LocalDate
import androidx.room.withTransaction

data class DiarySharePreview(
    val date: LocalDate,
    val text: String,
    val explanation: String,
    val author: String,
    val supervisor: String,
    val profile: JournalProfile,
    internal val fingerprint: String,
)

/** Prepare once, show exactly this text, then validate immediately before and after rendering.
 * Legacy prose has no trustworthy source links. With privacy controls active, rebuild a sharing
 * copy from current filtered records; never guess which old paragraphs are safe.
 */
class DiaryShareService(
    private val settings: SettingsRepository,
    private val visits: VisitRepository,
    private val events: EventRepository,
    private val places: PlaceRepository,
    private val diaries: DiaryRepository,
    private val database: com.dailybeat.app.data.db.DailyBeatDb,
) {
    suspend fun prepare(date: LocalDate, text: String): DiarySharePreview =
        database.withTransaction { prepareLocked(date, text) }

    private suspend fun prepareLocked(date: LocalDate, text: String): DiarySharePreview {
        val before = fingerprint(date)
        val settingsNow = settings.get()
        val visitsNow = visits.outboundVisitsForDate(date)
        val eventsNow = events.eventsForDate(date)
        val placesNow = places.all()
        val protected = placesNow.any { it.isPrivate } || visitsNow.any { it.hidden }
        val sharingText = if (protected) {
            val source = DayContextBuilder.buildDetailed(date, "", visitsNow, eventsNow, placesNow,
                profile = settingsNow.journalProfile)
            LocalDiaryBuilder.fromContext(date, settingsNow.journalProfile, source.text)
        } else text.trim()
        require(sharingText.isNotBlank()) { "There is no diary text to share." }
        check(before == fingerprint(date)) { "Records changed while preparing. Please try again." }
        return DiarySharePreview(
            date, sharingText,
            if (protected) "Privacy controls are active. This sharing copy uses current filtered records, not saved diary prose. Your original diary is unchanged. Check free-text notes for sensitive details."
            else "Draft · Review every place, time and note. The selected app will receive this text in a PDF. Export does not prove or submit an official record.",
            settingsNow.officerName,
            settingsNow.supervisorName.takeIf { settingsNow.journalProfile == JournalProfile.POLICE }.orEmpty(),
            settingsNow.journalProfile,
            before,
        )
    }

    suspend fun prepareWeek(end: LocalDate = LocalDate.now()): List<DiarySharePreview> =
        diaries.weekEnding(end).map { prepare(LocalDate.parse(it.dateKey), it.text) }

    suspend fun requireCurrent(preview: DiarySharePreview) {
        check(preview.fingerprint == database.withTransaction { fingerprint(preview.date) }) {
            "Records or privacy settings changed. Close this preview and review a fresh sharing copy."
        }
    }

    private suspend fun fingerprint(date: LocalDate): String {
        // Length-framed values prevent delimiter ambiguities. Any source or identity change invalidates.
        val fields = listOf(
            settings.get().toString(),
            visits.outboundVisitsForDate(date).sortedBy { it.id }.toString(),
            events.eventsForDate(date).sortedBy { it.id }.toString(),
            places.all().sortedBy { it.id }.toString(),
            diaries.textForDate(date).orEmpty(),
        )
        val framed = fields.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(framed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
