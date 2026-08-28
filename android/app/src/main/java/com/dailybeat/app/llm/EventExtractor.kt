package com.dailybeat.app.llm

import com.dailybeat.app.data.model.StructuredEvent
import org.json.JSONArray
import org.json.JSONObject

class EventExtractor(private val llm: LlmEngine) {

    suspend fun extract(transcript: String): StructuredEvent {
        if (llm.isModelAvailable()) {
            val prompt = """
                Extract a structured event from this voice note.
                Return ONLY valid JSON with these fields:
                - timestamp_guess (HH:MM or "unknown")
                - place_guess (string or "unknown")
                - people (array of names, empty if none)
                - case_numbers (array of strings, empty if none)
                - summary (one sentence)

                VOICE NOTE:
                $transcript
            """.trimIndent()

            llm.generate(prompt).getOrNull()?.let { response ->
                parseJsonResponse(response, transcript)?.let { return it }
            }
        }
        return regexFallback(transcript)
    }

    private fun parseJsonResponse(response: String, transcript: String): StructuredEvent? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            val obj = JSONObject(response.substring(start, end + 1))
            val people = jsonArrayToCsv(obj.optJSONArray("people"))
            val cases = jsonArrayToCsv(obj.optJSONArray("case_numbers"))
            val place = obj.optString("place_guess").takeIf { it.isNotBlank() && it != "unknown" }
            val summary = obj.optString("summary").takeIf { it.isNotBlank() } ?: transcript
            StructuredEvent(
                rawText = summary,
                placeName = place,
                peopleMentioned = people,
                caseNumbers = cases,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonArrayToCsv(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return (0 until array.length()).joinToString(", ") { array.optString(it) }.ifBlank { null }
    }

    private fun regexFallback(transcript: String): StructuredEvent {
        val fir = Regex("FIR\\s*[\\w/\\s]+", RegexOption.IGNORE_CASE).find(transcript)?.value?.trim()
        val place = when {
            transcript.contains("Market Beat", ignoreCase = true) -> "Market Beat"
            transcript.contains("Court", ignoreCase = true) -> "Court"
            transcript.contains("Office", ignoreCase = true) -> "Office"
            else -> null
        }
        val people = Regex("IO\\s+\\w+", RegexOption.IGNORE_CASE).findAll(transcript)
            .map { it.value.trim() }
            .joinToString(", ")
            .ifBlank { null }

        return StructuredEvent(
            rawText = transcript.trim(),
            placeName = place,
            peopleMentioned = people,
            caseNumbers = fir,
        )
    }
}
