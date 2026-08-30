package com.dailybeat.app.capture

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class VoiceRecorder(private val context: Context) {

  companion object {
    const val SAMPLE_RATE = 16000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
  }

  private var audioRecord: AudioRecord? = null

  suspend fun recordUntilSilence(maxSeconds: Int = 30, silenceMs: Long = 1500): FloatArray =
    withContext(Dispatchers.IO) {
      if (!PermissionHelper.hasRecordAudio(context)) {
        throw SecurityException("Microphone permission required.")
      }
      val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
      val recorder = try {
        AudioRecord(
          MediaRecorder.AudioSource.MIC,
          SAMPLE_RATE,
          CHANNEL,
          ENCODING,
          minBuffer * 2,
        )
      } catch (error: SecurityException) {
        // Permission can be revoked after the guard above.
        throw SecurityException("Microphone permission required.", error)
      }
      audioRecord = recorder
      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        throw IllegalStateException("Microphone not available.")
      }

      val samples = mutableListOf<Short>()
      val maxSamples = SAMPLE_RATE * maxSeconds
      var silentMs = 0L
      val silenceThreshold = 500
      val chunkMs = 100L
      val chunkSamples = (SAMPLE_RATE * chunkMs / 1000).toInt()
      val buffer = ShortArray(chunkSamples)

      recorder.startRecording()
      try {
        while (coroutineContext.isActive && samples.size < maxSamples) {
          val read = recorder.read(buffer, 0, buffer.size)
          if (read <= 0) continue
          for (i in 0 until read) {
            samples.add(buffer[i])
          }
          val rms = buffer.take(read).map { it.toInt() }.average()
          silentMs = if (rms < silenceThreshold) silentMs + chunkMs else 0L
          if (samples.size > SAMPLE_RATE && silentMs >= silenceMs) break
        }
      } finally {
        recorder.stop()
        recorder.release()
        audioRecord = null
      }

      samples.map { it / 32768.0f }.toFloatArray()
    }

  fun cancel() {
    audioRecord?.let {
      try {
        it.stop()
      } catch (_: Exception) {
      }
      it.release()
    }
    audioRecord = null
  }
}

object VoiceTranscriptProvider {
  fun emulatorDemoTranscript(): String? {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    if (fingerprint.contains("generic") || model.contains("sdk_gphone") || model.contains("emulator")) {
      return "Eleven forty, Market Beat, met IO Rajan, inspected chain snatching case FIR two four seven slash twenty six"
    }
    return null
  }
}
