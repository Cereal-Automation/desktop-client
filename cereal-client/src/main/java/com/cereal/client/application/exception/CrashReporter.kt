package com.cereal.client.application.exception

import io.sentry.Sentry

/**
 * Single entry point for fire-and-forget crash reporting outside the
 * [com.cereal.client.application.Interactor] pipeline — background coroutines, UI callbacks, and
 * infrastructure adapters.
 *
 * Applies [ExceptionFilter] uniformly so every ad-hoc capture site honours the same "known-noise"
 * policy (shutdown [java.io.EOFException]s, cancellations, socket timeouts) instead of each `catch`
 * block re-deciding — or forgetting to. [com.cereal.client.application.Interactor] keeps its own
 * richer classification for the use-case pipeline.
 */
object CrashReporter {
    fun report(
        throwable: Throwable,
        context: Map<String, Any?> = emptyMap(),
    ) {
        if (ExceptionFilter.shouldIgnoreException(throwable)) {
            return
        }
        // An exception carrying diagnostics (a signature failure, an API error) reaching an ad-hoc
        // catch site — a background job, a script callback — used to be reported with no context at
        // all, because only the Interactor pipeline enriched it. That made the report untriageable:
        // no URL, no HTTP status, no CDN cache headers to tell an unsigned edge response apart from
        // genuine tampering. Attach them here so every capture route carries them.
        val diagnostics = throwable.findSentryDiagnostics()
        if (context.isEmpty() && diagnostics == null) {
            Sentry.captureException(throwable)
        } else {
            Sentry.captureException(throwable) { scope ->
                context.forEach { (key, value) -> scope.setContexts(key, value.toString()) }
                diagnostics?.let { scope.setContexts(it.sentryContextKey, it.sentryContext()) }
            }
        }
    }
}
