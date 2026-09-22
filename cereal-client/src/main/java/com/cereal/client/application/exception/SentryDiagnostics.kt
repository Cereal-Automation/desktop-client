package com.cereal.client.application.exception

/**
 * Implemented by exceptions that carry structured evidence worth attaching to their Sentry report.
 *
 * Exists so the reporting paths ([com.cereal.client.application.Interactor] and [CrashReporter]) can
 * enrich a report without knowing about individual exception types — in particular without the
 * application layer importing infrastructure exceptions such as `ApiException`.
 *
 * Keep [sentryContext] free of credentials, tokens, and personal data: it is attached verbatim to a
 * crash report. Prefer allow-listing fields over dumping whatever a response happened to contain.
 */
interface SentryDiagnostics {
    /** Name of the Sentry context block the payload is attached under, e.g. `invalid_signature`. */
    val sentryContextKey: String

    /**
     * Context payload for Sentry. Must be a map (not a bare scalar) so Sentry's ingest pipeline
     * accepts it as an object — a non-`String` scalar is serialized bare and silently discarded.
     */
    fun sentryContext(): Map<String, Any>
}

/**
 * Finds the nearest [SentryDiagnostics] in this throwable's cause chain, since ad-hoc catch sites
 * frequently report a wrapper rather than the original. Depth-bounded to survive cause cycles.
 */
fun Throwable.findSentryDiagnostics(): SentryDiagnostics? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current is SentryDiagnostics) return current
        current = current.cause
        depth++
    }
    return null
}

private const val MAX_CAUSE_DEPTH = 10
