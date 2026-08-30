package com.dailybeat.app.ui.settings

object PlaceInputValidator {
    fun errorFor(name: String, latitude: String, longitude: String): String? {
        if (name.isBlank()) return "Enter a place name."

        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) {
            return "Enter valid latitude and longitude."
        }
        if (lat !in -90.0..90.0) return "Latitude must be between -90 and 90."
        if (lon !in -180.0..180.0) return "Longitude must be between -180 and 180."
        return null
    }
}
