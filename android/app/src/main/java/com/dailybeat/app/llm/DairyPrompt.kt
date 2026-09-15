package com.dailybeat.app.llm

const val DAIRY_SYSTEM_PROMPT =
    "You help a person prepare a daily journal draft. " +
        "Convert the following raw events from the day into a formal diary entry " +
        "using the chosen journal template. Use only the information given. Do not invent " +
        "details. Use past tense for completed actions. Keep it concise."

fun buildDairyPrompt(events: String): String =
    """
    $DAIRY_SYSTEM_PROMPT

    EVENTS:
    $events

    DAIRY:
    """.trimIndent()
