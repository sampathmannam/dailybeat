package com.dailybeat.app.data.settings

data class AppSettings(
    val officerName: String = "IPS Officer",
    val gpsCaptureEnabled: Boolean = true,
    val callLogEnabled: Boolean = false,
)
