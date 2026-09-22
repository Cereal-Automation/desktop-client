package com.cereal.client.domain.model.url

import com.cereal.client.domain.model.exception.InvalidWebUrlException
import java.net.URI
import java.net.URISyntaxException

/**
 * A validated absolute http(s) URL.
 *
 * A [WebUrl] can only ever hold a URL whose scheme is http or https; relative strings,
 * unparseable input, and non-web schemes such as file/smb/javascript are rejected at construction.
 *
 * This is the single source of truth for "is it safe to hand this URL to a browser/OS opener".
 * Untrusted URLs (e.g. a marketplace script manifest's supportUrl) must be parsed into a [WebUrl]
 * before reaching any handler, otherwise dangerous schemes become a one-click credential-leak or
 * local-file vector.
 *
 * Construct via [parse] (returns null for invalid input) or [of] (throws [InvalidWebUrlException]).
 * The wrapped [uri] is guaranteed to carry an http(s) scheme.
 */
@JvmInline
value class WebUrl private constructor(
    val uri: URI,
) {
    /** The URL rendered as a string, e.g. `https://example.com/path`. */
    val value: String get() = uri.toString()

    /** The host component, or null when the URL carries no host. */
    val host: String? get() = uri.host

    /** The path component, or the empty string when absent. */
    val path: String get() = uri.path ?: ""

    override fun toString(): String = value

    companion object {
        /** Parses [raw] into a [WebUrl], or returns null when it is not a valid absolute http(s) URL. */
        fun parse(raw: String): WebUrl? {
            val uri =
                try {
                    URI(raw)
                } catch (_: URISyntaxException) {
                    return null
                }
            val scheme = uri.scheme?.lowercase()
            return if (scheme == "http" || scheme == "https") WebUrl(uri) else null
        }

        /**
         * Parses [raw] into a [WebUrl], throwing [InvalidWebUrlException] when it is not a valid
         * absolute http(s) URL.
         */
        fun of(raw: String): WebUrl = parse(raw) ?: throw InvalidWebUrlException("Not a valid http(s) URL: $raw")
    }
}

/**
 * Returns true only when [url] is a valid absolute http(s) URL. Backed by [WebUrl.parse], so it
 * shares the exact safety semantics; prefer parsing into a [WebUrl] directly when the parsed value
 * is also needed.
 */
fun isWebUrl(url: String): Boolean = WebUrl.parse(url) != null
