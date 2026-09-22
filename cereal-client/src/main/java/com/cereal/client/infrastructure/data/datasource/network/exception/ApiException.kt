package com.cereal.client.infrastructure.data.datasource.network.exception

import com.cereal.client.application.exception.SentryDiagnostics

/**
 * A non-2xx API response, or one whose body could not be parsed.
 *
 * Carries the HTTP status and CDN diagnostics so an edge-generated failure (rate limit, WAF block,
 * 502/503) is traceable in Sentry. Without them an edge HTML error page produced a report reading
 * only "Unknown error." — accurate, and useless to triage.
 *
 * [validationErrors] is deliberately kept out of [sentryContext]: server validation messages echo
 * user input (email addresses and the like), which must not land in a crash report. Only their count
 * is reported.
 */
class ApiException(
    override val message: String,
    val validationErrors: List<String>? = null,
    val httpStatus: Int? = null,
    val url: String? = null,
    val edgeHeaders: Map<String, String> = emptyMap(),
) : Exception(message),
    SentryDiagnostics {
    override val sentryContextKey = "api_error"

    override fun sentryContext(): Map<String, Any> =
        buildMap {
            httpStatus?.let { put("http_status", it) }
            url?.let { put("url", it) }
            put("validation_error_count", validationErrors?.size ?: 0)
            edgeHeaders.forEach { (name, value) -> put(name, value) }
        }
}
