package com.dailybeat.app.llm

const val DAIRY_SYSTEM_PROMPT =
    "You are an Indian Police Service officer writing your official daily diary. " +
        "Convert the following raw events from the day into a formal diary entry " +
        "in standard IPS diary format. Use only the information given. Do not invent " +
        "details. Use present tense for completed actions. Keep it concise."

fun buildDairyPrompt(events: String): String =
    """
    $DAIRY_SYSTEM_PROMPT

    EVENTS:
    $events

    DAIRY:
    """.trimIndent()
