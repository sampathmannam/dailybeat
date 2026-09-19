package com.dailybeat.app.domain

/** Old versions stored GPS fallback text in address AND a truncated latitude in placeName. */
object OutboundPlaceLabel {
    private val coordinate = Regex("(?i)(?:location\\s*)?[-+]?\\d{1,3}\\.\\d{3,}(?:\\s*[,/]\\s*[-+]?\\d{1,3}\\.\\d{3,})?")
    fun safe(value: String?): String? = value?.takeUnless { coordinate.containsMatchIn(it) }
}
