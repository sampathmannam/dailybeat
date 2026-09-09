package com.dailybeat.app.capture

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.model.StructuredEvent

/** Orchestrates speech recognition and optional cloud structuring into a saved event. */
class VoiceCaptureOrchestrator(private val app: DailyBeatApp) {

    suspend fun captureAndSave(): Result<String> {
        val context = app.applicationContext
        val transcriber = SpeechTranscriber(context)
        var transcript = ""
        if (transcriber.isAvailable()) {
            transcript = transcriber.transcribe().trim()
        }
        if (transcript.isBlank()) {
            return Result.failure(IllegalStateException("Voice not recognized. Try again or use optional note."))
        }

        val cloudReady = runCatching { app.settingsRepository.isCloudBrainReady() }.getOrDefault(false)
        val extraction = if (cloudReady) {
            app.eventExtractor.extract(transcript)
        } else {
            Result.failure(IllegalStateException("Cloud AI is not configured."))
        }
        val structured = extraction.getOrElse { error ->
            if (cloudReady) {
                OperationalFailureLog.record(
                    context = context,
                    category = "voice-structure",
                    retryable = true,
                    message = "Voice transcript was saved without cloud enrichment " +
                        "(${error.javaClass.simpleName}).",
                )
            }
            StructuredEvent(rawText = transcript)
        }
        app.eventRepository.addStructuredEvent(structured, type = "voice")
        val enrichment = if (extraction.isSuccess) "cloud-enriched" else "local transcript"
        CaptureAuditLog.log(context, "voice", "Saved $enrichment (${transcript.length} characters)")
        return Result.success(transcript)
    }
}
