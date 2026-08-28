package com.dailybeat.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlmEngine(private val ctx: Context) {

    companion object {
        const val MODEL_ASSET = "dailybeat-q4_k_m.gguf"
        const val MODEL_FILE = "dailybeat-q4_k_m.gguf"
    }

    private var inference: LlmInference? = null

    fun isModelAvailable(): Boolean {
        return try {
            ctx.assets.openFd(MODEL_ASSET).use { true }
        } catch (_: Exception) {
            val file = modelFile()
            file.exists() && file.length() > 0L
        }
    }

    suspend fun generateDairy(events: String): Result<String> {
        if (!isModelAvailable()) {
            return Result.failure(
                IllegalStateException(
                    "GGUF model not found. Copy dailybeat-q4_k_m.gguf to app/src/main/assets/ " +
                        "or to internal storage after fine-tune (Phase 5).",
                ),
            )
        }
        return generate(buildDairyPrompt(events))
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            val response = ensureLoaded().generateResponse(prompt)
            Result.success(response.trim())
        } catch (exc: Exception) {
            Result.failure(exc)
        }
    }

    private fun ensureLoaded(): LlmInference {
        inference?.let { return it }
        copyAssetIfNeeded()
        val loaded = LlmInference.createFromOptions(
            ctx,
            LlmInferenceOptions.builder()
                .setModelPath(modelFile().absolutePath)
                .setMaxTokens(1024)
                .setTemperature(0.1f)
                .setTopK(40)
                .build(),
        )
        inference = loaded
        return loaded
    }

    private fun modelFile(): File = File(ctx.filesDir, MODEL_FILE)

    private fun copyAssetIfNeeded() {
        val out = modelFile()
        if (out.exists() && out.length() > 0L) {
            return
        }
        ctx.assets.open(MODEL_ASSET).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
