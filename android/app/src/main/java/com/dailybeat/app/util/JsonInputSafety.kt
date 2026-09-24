package com.dailybeat.app.util

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/**
 * Iterative JSON preflight before untrusted input reaches recursive org.json parsing.
 * Callers retain responsibility for their own input byte limits.
 */
internal fun isBoundedJson(payload: String, objectOnly: Boolean = false): Boolean {
    return try {
        JsonReader(StringReader(payload)).use { reader ->
            reader.isLenient = false
            val root = reader.peek()
            if (root != JsonToken.BEGIN_OBJECT && (objectOnly || root != JsonToken.BEGIN_ARRAY)) return false
            var depth = 0
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        if (++depth > MAX_JSON_NESTING) return false
                        reader.beginObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        if (++depth > MAX_JSON_NESTING) return false
                        reader.beginArray()
                    }
                    JsonToken.END_OBJECT -> { reader.endObject(); depth-- }
                    JsonToken.END_ARRAY -> { reader.endArray(); depth-- }
                    JsonToken.NAME -> reader.nextName()
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    JsonToken.END_DOCUMENT -> Unit
                }
            }
            depth == 0
        }
    } catch (_: Exception) {
        false
    }
}

private const val MAX_JSON_NESTING = 64
