package com.dailybeat.app.audit

import java.io.File
import java.io.RandomAccessFile

/** Read only a bounded tail, including when a corrupted legacy log contains one enormous line. */
internal fun newestLogLines(file: File, limit: Int, maxBytes: Int): List<String> {
    if (limit <= 0 || !file.isFile) return emptyList()
    require(maxBytes > 0)
    return RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val start = (length - maxBytes).coerceAtLeast(0)
        input.seek(start)
        val bytes = ByteArray((length - start).toInt())
        input.readFully(bytes)
        val tail = bytes.toString(Charsets.UTF_8)
        val completeLines = if (start > 0) tail.substringAfter('\n', "") else tail
        completeLines.lineSequence().filter { it.isNotEmpty() }.toList().takeLast(limit)
    }
}
