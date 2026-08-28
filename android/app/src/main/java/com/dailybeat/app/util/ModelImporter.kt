package com.dailybeat.app.util

import android.content.Context
import android.os.Environment
import com.dailybeat.app.llm.LlmEngine
import java.io.File

class ModelImporter(private val context: Context) {

  fun importFromDownloads(): Boolean {
    val candidates = mutableListOf<File>()

    @Suppress("DEPRECATION")
    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (publicDownloads.isDirectory) {
      candidates += publicDownloads.listFiles().orEmpty()
    }

    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
      if (dir.isDirectory) candidates += dir.listFiles().orEmpty()
    }

    val gguf = candidates.firstOrNull { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
      ?: return false

    val target = File(context.filesDir, LlmEngine.MODEL_FILE)
    gguf.inputStream().use { input ->
      target.outputStream().use { output -> input.copyTo(output) }
    }
    return true
  }

  fun hasBundledOrLocalModel(): Boolean {
    return try {
      context.assets.openFd(LlmEngine.MODEL_ASSET).close()
      true
    } catch (_: Exception) {
      val file = File(context.filesDir, LlmEngine.MODEL_FILE)
      file.exists() && file.length() > 0L
    }
  }
}
