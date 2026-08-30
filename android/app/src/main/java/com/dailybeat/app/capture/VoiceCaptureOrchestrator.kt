package com.dailybeat.app.capture

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog

/** Orchestrates speech recognition and cloud structuring into a saved event. */
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

        val structured = app.eventExtractor.extract(transcript).getOrElse { return Result.failure(it) }
        app.eventRepository.addStructuredEvent(structured, type = "voice")
        CaptureAuditLog.log(context, "voice", transcript.take(120))
        return Result.success(transcript)
    }
}
