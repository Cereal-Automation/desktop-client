package com.cereal.client.application.exception

import io.sentry.SentryEvent
import kotlinx.coroutines.CancellationException
import java.io.EOFException

object ExceptionFilter {
    fun shouldIgnoreException(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }.any {
            it is EOFException || it is CancellationException || it is java.net.SocketTimeoutException
        }

    fun shouldIgnoreSentryEvent(event: SentryEvent): Boolean =
        event.exceptions.orEmpty().any {
            val type = it.type
            val value = it.value
            type?.contains("EOFException") == true || type?.contains("CancellationException") == true || type?.contains("SocketTimeoutException") == true ||
                // kdriver registers a JVM shutdown hook to kill the spawned browser process. Its lazily-loaded
                // inner lambda gets defined by the system classloader at exit while the enclosing class lives in
                // the app's custom classloader, throwing IllegalAccessError. Harmless shutdown noise: our own
                // browser.stop() calls already handle cleanup. Scoped tightly so real IllegalAccessErrors surface.
                (type?.contains("IllegalAccessError") == true && value?.contains("addShutdownHook") == true)
        }
}
