package com.dailybeat.app.cloud

/** Replaces an app-owned section without touching officer-written text before or after it. */
internal object GeneratedDiaryBlock {
    fun merge(
        existing: String,
        startPrefix: String,
        endMarker: String,
        replacement: String,
    ): String {
        val start = existing.indexOf(startPrefix)
        if (start < 0) return append(existing, replacement)
        val endStart = existing.indexOf(endMarker, start + startPrefix.length)
        if (endStart < 0) {
            // A legacy or manually edited block has no reliable boundary. Preserve it and append
            // the bounded form; duplication is safer than deleting officer-authored text.
            return append(existing, replacement)
        }
        val end = endStart + endMarker.length
        return listOf(
            existing.substring(0, start).trimEnd(),
            replacement.trim(),
            existing.substring(end).trimStart(),
        ).filter(String::isNotBlank).joinToString("\n\n")
    }

    private fun append(existing: String, replacement: String): String =
        listOf(existing.trim(), replacement.trim())
            .filter(String::isNotBlank)
            .joinToString("\n\n")
}
