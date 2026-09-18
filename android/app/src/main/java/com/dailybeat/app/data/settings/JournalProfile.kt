package com.dailybeat.app.data.settings

/** Presentation templates share the same private record; switching never changes captured data. */
enum class JournalProfile(val id: String, val title: String, val description: String) {
    PERSONAL("personal", "Personal", "Places, moments and memories from your day."),
    FIELD_WORK("field_work", "Field work", "Visits, work notes and follow-ups."),
    POLICE("police", "Police", "Officer details and a formal daily diary template."),
    ;

    val documentTitle: String get() = when (this) {
        PERSONAL -> "Daily journal"
        FIELD_WORK -> "Workday journal"
        POLICE -> "Daily diary"
    }

    val instruction: String get() = when (this) {
        PERSONAL -> "Write a concise personal journal in natural language. Do not use police or official-report terminology."
        FIELD_WORK -> "Write a concise professional workday journal. Describe only supplied visits and work notes."
        POLICE -> "Write a formal daily diary draft for a police officer. Do not claim submission or official verification."
    }

    companion object {
        fun fromId(id: String?): JournalProfile = entries.find { it.id == id } ?: PERSONAL
    }
}
