package com.dailybeat.app.util

import android.content.Context
import java.io.File

/**
 * Where DailyBeat keeps the files it produces (PDFs, exports, the capture audit log).
 *
 * External app storage is preferred because it is what the FileProvider shares from, but it is
 * null while external storage is unavailable, so fall back to internal storage rather than
 * writing to a relative path that fails at the first mkdirs().
 */
object AppStorage {

    private const val DIR_NAME = "DailyBeat"

    fun outputDir(context: Context): File {
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        return File(parent, DIR_NAME).apply { mkdirs() }
    }

    fun outputFile(context: Context, name: String): File = File(outputDir(context), name)
}
