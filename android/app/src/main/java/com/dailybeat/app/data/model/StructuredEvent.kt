package com.dailybeat.app.data.model

data class StructuredEvent(
    val rawText: String,
    val placeName: String? = null,
    val peopleMentioned: String? = null,
    val caseNumbers: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
