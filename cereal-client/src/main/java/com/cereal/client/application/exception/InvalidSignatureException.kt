package com.cereal.client.application.exception

import java.io.IOException

/**
 * Thrown when a **successful** marketplace API response fails signature verification.
 *
 * Extends [IOException] to honour the OkHttp interceptor contract, but is deliberately a distinct
 * type: unlike an ordinary [IOException] (timeouts, connectivity), an unverifiable 2xx body signals
 * response tampering, a replayed/cached response, or a public-key mismatch and must be reported to
 * Sentry. The interactor error mapping matches this type explicitly so it is surfaced rather than
 * treated as expected network noise.
 *
 * Several very different situations used to collapse into one indistinguishable exception (an
 * unsigned edge error page, a cache replay, a mutated body), so the fields below are carried through
 * to Sentry via [sentryContext]. See [reason] for the discriminator.
 */
class InvalidSignatureException(
    val url: String,
    val httpStatus: Int,
    val reason: String,
    /**
     * Non-sensitive response headers (CDN ray id, cache status, age, …) that distinguish an edge
     * replay or an unsigned edge response from genuine tampering. Never contains credentials.
     */
    val edgeHeaders: Map<String, String> = emptyMap(),
) : IOException("The response signature could not be verified. (reason=$reason)"),
    SentryDiagnostics {
    override val sentryContextKey = "invalid_signature"

    override fun sentryContext(): Map<String, Any> =
        buildMap {
            put("url", url)
            put("http_status", httpStatus)
            put("reason", reason)
            edgeHeaders.forEach { (name, value) -> put(name, value) }
        }

    companion object {
        /** A 2xx response arrived without an `X-Signature` header — it never reached the signer. */
        const val REASON_HEADER_ABSENT = "signature_header_absent"

        /** The `X-Signature` header was not valid Base64. */
        const val REASON_NOT_BASE64 = "signature_not_base64"

        /** Signature decoded and verified cleanly, but does not match salt + body. */
        const val REASON_MISMATCH = "signature_mismatch"

        /** The JCE rejected the key or the signature bytes; suffix carries the exception class. */
        const val REASON_CRYPTO_ERROR = "crypto_error"
    }
}
