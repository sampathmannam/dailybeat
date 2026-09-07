package com.dailybeat.app.cloud

object ContextLimiter {
    private const val MAX_CHARS = 14_000

    fun trimForLlm(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val candidate = text.take(MAX_CHARS - 200)
        val head = candidate.substringBeforeLast('\n', candidate)
        return head + "\n\n[TRUNCATED: ${text.length - head.length} chars omitted for model limits]"
    }
}
