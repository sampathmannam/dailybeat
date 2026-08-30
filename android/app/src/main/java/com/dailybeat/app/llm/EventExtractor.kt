package com.dailybeat.app.llm

import com.dailybeat.app.cloud.CloudLlmClient
import com.dailybeat.app.cloud.DayContextBuilder
import com.dailybeat.app.data.model.StructuredEvent
import com.dailybeat.app.data.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

class EventExtractor(
    private val cloudLlm: CloudLlmClient,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun extract(transcript: String): Result<StructuredEvent> {
        val settings = settingsRepository.get()
        if (!settingsRepository.isCloudBrainReady()) {
            return Result.failure(IllegalStateException("Cloud AI is required. Enable it and add an API key in Settings."))
        }
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

        return cloudLlm.generate(settings, DayContextBuilder.SYSTEM_PROMPT, prompt).fold(
            onSuccess = { response ->
                parseJsonResponse(response, transcript)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("The cloud model returned invalid event JSON."))
            },
            onFailure = { Result.failure(it) },
        )
    }

    private fun parseJsonResponse(response: String, transcript: String): StructuredEvent? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val obj = JSONObject(response.substring(start, end + 1))
            StructuredEvent(
                rawText = obj.optString("summary").takeIf { it.isNotBlank() } ?: transcript,
                placeName = obj.optString("place_guess").takeIf { it.isNotBlank() && it != "unknown" },
                peopleMentioned = jsonArrayToCsv(obj.optJSONArray("people")),
                caseNumbers = jsonArrayToCsv(obj.optJSONArray("case_numbers")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonArrayToCsv(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return (0 until array.length()).joinToString(", ") { array.optString(it) }.ifBlank { null }
    }
}
