package com.dailybeat.app.maps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resumable transfers into staging only. Activation belongs to OfflineMapRepository. */
internal class MapPackInstaller(private val client: OkHttpClient) {
    suspend fun download(parts: List<MapPart>, destination: File, progress: (Long) -> Unit) {
        destination.parentFile!!.mkdirs()
        val total = parts.sumOf { it.bytes }
        if (destination.length() > total) destination.delete()
        var offset = 0L
        for (part in parts) {
            currentCoroutineContext().ensureActive()
            if (destination.length() >= offset + part.bytes) {
                if (sha256(destination, offset, part.bytes) == part.sha256) {
                    offset += part.bytes
                    progress(offset)
                    continue
                }
                RandomAccessFile(destination, "rw").use { it.setLength(offset) }
            }
            val start = (destination.length() - offset).coerceAtLeast(0)
            transfer(part, destination, offset, start, progress)
            if (sha256(destination, offset, part.bytes) != part.sha256) {
                RandomAccessFile(destination, "rw").use { it.setLength(offset) }
                throw IOException("Map download failed verification. Retry to download the damaged part again.")
            }
            offset += part.bytes
        }
    }

    private suspend fun transfer(part: MapPart, file: File, offset: Long, start: Long, progress: (Long) -> Unit) {
        val request = Request.Builder().url(part.url).header("Accept-Encoding", "identity")
            .apply { if (start > 0) header("Range", "bytes=$start-") }.build()
        val call = client.newCall(request)
        val finished = CompletableDeferred<Unit>()
        try { suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(error)
                    finished.complete(Unit)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val actualStart = if (it.code == 200) 0L else start
                            if (it.code != 200 && (it.code != 206 ||
                                it.header("Content-Range") != "bytes $start-${part.bytes - 1}/${part.bytes}")) {
                                throw IOException("Map download is unavailable. Try again later.")
                            }
                            val body = it.body ?: throw IOException("Empty map download")
                            if (body.contentLength() >= 0 && body.contentLength() != part.bytes - actualStart) {
                                throw IOException("Unexpected map download size")
                            }
                            RandomAccessFile(file, "rw").use { output ->
                                output.setLength(offset + actualStart)
                                output.seek(offset + actualStart)
                                var written = actualStart
                                val bytes = ByteArray(64 * 1024)
                                body.byteStream().use { input ->
                                    while (true) {
                                        if (continuation.isCancelled) throw IOException("Download paused")
                                        val count = input.read(bytes)
                                        if (count < 0) break
                                        if (written + count > part.bytes) throw IOException("Map download exceeds its size limit")
                                        output.write(bytes, 0, count)
                                        written += count
                                        progress(offset + written)
                                    }
                                }
                                if (written != part.bytes) throw IOException("Map download was interrupted. Resume to continue.")
                                output.fd.sync()
                            }
                        }
                        if (!continuation.isCancelled) continuation.resume(Unit)
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    } finally { finished.complete(Unit) }
                }
            })
        } } finally {
            // Cancellation must wait for the writer to close before another worker/delete can
            // acquire the repository lock and touch this staging file.
            withContext(NonCancellable) { finished.await() }
        }
    }

    suspend fun unpack(zip: File, destination: File, maxBytes: Long) {
        var total = 0L
        var entries = 0
        val paths = mutableSetOf<String>()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = input.nextEntry ?: break
                require(++entries <= 4096) { "Too many map resources." }
                val target = File(destination, entry.name)
                require(!entry.name.startsWith('/') && !entry.name.split('/').contains("..") &&
                    target.canonicalPath.startsWith(destination.canonicalPath + File.separator)) { "Invalid map resource path." }
                require(paths.add(target.canonicalPath)) { "Duplicate map resource." }
                require(entry.name !in setOf("tamil-nadu.pmtiles", "assets.zip", "manifest.json")) { "Reserved map resource path." }
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile!!.mkdirs()
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "Map resources exceed their size limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        require(total == maxBytes) { "Incomplete map resources." }
    }
}

internal suspend fun sha256(file: File, offset: Long = 0, length: Long = file.length()): String {
    val digest = MessageDigest.getInstance("SHA-256")
    RandomAccessFile(file, "r").use { input ->
        input.seek(offset)
        var remaining = length
        val bytes = ByteArray(64 * 1024)
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val count = input.read(bytes, 0, minOf(remaining, bytes.size.toLong()).toInt())
            if (count < 0) throw IOException("Incomplete map file")
            digest.update(bytes, 0, count)
            remaining -= count
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
