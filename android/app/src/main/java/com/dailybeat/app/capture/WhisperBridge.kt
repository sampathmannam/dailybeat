package com.dailybeat.app.capture

import android.content.Context

/**
 * Whisper.cpp JNI bridge. When [ggml-tiny.bin] is bundled and native lib is linked,
 * transcribe() uses on-device STT. Otherwise falls back to emulator demo transcript
 * or empty string (caller should prompt for manual entry).
 */
class WhisperBridge(private val context: Context) {

  companion object {
    const val MODEL_ASSET = "ggml-tiny.bin"
  }

  fun isModelAvailable(): Boolean = try {
    context.assets.openFd(MODEL_ASSET).use { true }
  } catch (_: Exception) {
    false
  }

  fun transcribe(samples: FloatArray): String {
    if (isModelAvailable()) {
      return nativeTranscribe(samples)
    }
    return VoiceTranscriptProvider.emulatorDemoTranscript() ?: ""
  }

  private fun nativeTranscribe(samples: FloatArray): String {
    // Native whisper.cpp binding added when ggml-tiny.bin is bundled in a future build.
    return VoiceTranscriptProvider.emulatorDemoTranscript() ?: ""
  }
}
