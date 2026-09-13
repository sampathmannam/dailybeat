package com.dailybeat.app.util

/**
 * Shared input limits for every user-editable surface.
 *
 * UI limits keep Compose state and SavedStateHandle small, while repository-level use keeps the
 * same boundary in force for imports, tests, workers, and future callers that bypass a screen.
 * Unicode is preserved: [bounded] never cuts an emoji or supplementary-script character between
 * its UTF-16 surrogate pair.
 */
object InputPolicy {
    const val PERSON_NAME_CHARS = 120
    const val PLACE_NAME_CHARS = 120
    const val BEAT_TITLE_CHARS = 120
    const val MOMENT_NOTE_CHARS = 8_000
    const val DIARY_CHARS = 50_000
    const val CUSTOM_EVENTS_CHARS = 12_000
    const val BACKUP_EMAIL_CHARS = 320
    const val BACKUP_PASSWORD_CHARS = 1_024
    const val API_KEY_CHARS = 4_096
    const val CLOUD_MODEL_CHARS = 200
    const val CLOUD_URL_CHARS = 2_048

    fun bounded(value: String, maxChars: Int): String {
        require(maxChars >= 0)
        if (value.length <= maxChars) return value
        var end = maxChars
        if (
            end > 0 &&
            end < value.length &&
            Character.isHighSurrogate(value[end - 1]) &&
            Character.isLowSurrogate(value[end])
        ) {
            end -= 1
        }
        return value.substring(0, end)
    }

    /** Single-line fields accept every writing system but never persist pasted line/control data. */
    fun singleLine(value: String, maxChars: Int): String = bounded(
        buildString(value.length.coerceAtMost(maxChars)) {
            value.forEach { char ->
                when {
                    char == '\n' || char == '\r' -> append(' ')
                    !char.isISOControl() -> append(char)
                }
            }
        },
        maxChars,
    )

    /** Multiline text keeps intentional tabs/newlines while dropping other control characters. */
    fun multiline(value: String, maxChars: Int): String = bounded(
        buildString(value.length.coerceAtMost(maxChars)) {
            value.forEach { char ->
                when {
                    char == '\r' -> Unit
                    char == '\n' || char == '\t' || !char.isISOControl() -> append(char)
                }
            }
        },
        maxChars,
    )
}
