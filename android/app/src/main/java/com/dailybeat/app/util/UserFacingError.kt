package com.dailybeat.app.util

import com.dailybeat.app.cloud.CloudRequestException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns a failure into words the officer can act on.
 *
 * Messages the app wrote itself pass through unchanged: every IllegalStateException and
 * IllegalArgumentException raised in this codebase carries a sentence meant for the screen, and
 * CloudRequestException's message is already curated. Everything else — the network stack, Room,
 * JSON parsing, the file system — is mapped to a plain cause, because
 * `Unable to resolve host "api.deepseek.com": No address associated with hostname` is not a
 * sentence to put in front of anyone, and PRODUCT.md rules out exception text in error states.
 */
fun Throwable.userMessage(fallback: String): String {
    val own = message?.takeIf { it.isNotBlank() }
    return when (this) {
        is CloudRequestException -> own ?: fallback
        is UnknownHostException -> "No internet connection. Check the network and try again."
        is SocketTimeoutException -> "The connection timed out. Try again."
        is SSLException -> "A secure connection could not be made. Check the network and try again."
        is IOException -> "A network or storage problem interrupted this. Try again."
        is IllegalStateException, is IllegalArgumentException -> own ?: fallback
        else -> fallback
    }
}
