package com.dailybeat.app.capture

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.model.StructuredEvent
import kotlinx.coroutines.CancellationException

/** Orchestrates speech recognition and optional cloud structuring into a saved event. */
class VoiceCaptureOrchestrator(
    private val app: DailyBeatApp,
    private val transcribe: suspend () -> String = {
        val transcriber = SpeechTranscriber(app.applicationContext)
        if (transcriber.isAvailable()) transcriber.transcribe() else ""
    },
    private val canEnrich: suspend () -> Boolean = {
        app.settingsRepository.isCloudBrainReady() && app.permitsUnlinkedCloudText()
    },
    private val extract: suspend (String) -> Result<StructuredEvent> = { app.eventExtractor.extract(it) },
) {

    suspend fun captureAndSave(): Result<String> = try {
        val generation = CaptureStorageGate.dataGeneration.get()
        val context = app.applicationContext
        val transcript = transcribe().trim()
        if (transcript.isBlank()) {
            throw IllegalStateException("Voice not recognized. Try again or use optional note.")
        }

        requireCurrentData(generation)
        val cloudReady = try { canEnrich() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
        // Both recognition and the privacy lookup suspend. Never send an old voice note after
        // erase/restore completed while either was pending. Do not hold the storage gate on HTTP.
        requireCurrentData(generation)
        val extraction = if (cloudReady) {
            extract(transcript)
        } else {
            Result.failure(IllegalStateException("Cloud AI is not configured."))
        }
        extraction.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val structured = extraction.getOrElse { StructuredEvent(rawText = transcript) }
        CaptureStorageGate.writeIfCurrent(generation) {
            app.eventRepository.addStructuredEvent(structured, type = "voice")
            if (cloudReady && extraction.isFailure) {
                OperationalFailureLog.record(
                    context = context,
                    category = "voice-structure",
                    retryable = true,
                    message = "Voice transcript was saved without cloud enrichment " +
                        "(${extraction.exceptionOrNull()!!.javaClass.simpleName}).",
                )
            }
            val enrichment = if (extraction.isSuccess) "cloud-enriched" else "local transcript"
            CaptureAuditLog.log(context, "voice", "Saved $enrichment (${transcript.length} characters)")
        }
        Result.success(transcript)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun requireCurrentData(expected: Long) {
        check(expected == CaptureStorageGate.dataGeneration.get()) {
            "Local data changed. Record this voice note again before saving."
        }
    }
}
