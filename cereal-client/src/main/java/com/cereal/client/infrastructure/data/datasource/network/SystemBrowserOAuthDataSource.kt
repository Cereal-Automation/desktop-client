package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.exception.OAuthAuthenticationException
import com.cereal.client.domain.model.auth.OAuthProvider
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Brokered SSO sign-in via the system browser + a throwaway `127.0.0.1` loopback listener. Serves
 * every [OAuthProvider] — only the backend URL segment (`provider.slug`) differs.
 *
 * Flow: generate a high-entropy `state`, bind a loopback HTTP server on an ephemeral port, open the
 * system browser at `<marketplace>/auth/<slug>/desktop?state=…&redirect=http://127.0.0.1:<port>`,
 * and suspend until the backend redirects back with `?code&state`. The `state` is verified to reject
 * an injected code, and the socket is bound to `127.0.0.1` only so it is unreachable off-machine.
 *
 * [openBrowser] is injected (rather than calling AWT directly) so the loopback/state logic is
 * testable without launching a real browser; production wires it to the OS browser datasource.
 */
class SystemBrowserOAuthDataSource(
    private val config: ApplicationConfig,
    private val openBrowser: (String) -> Boolean,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : OAuthDataSource {
    private val logger = LoggerFactory.getLogger(SystemBrowserOAuthDataSource::class.java)

    override suspend fun obtainOneTimeCode(provider: OAuthProvider): String {
        val state = generateState()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0), 0)
        val result = CompletableDeferred<String>()

        try {
            server.createContext("/") { exchange -> handleCallback(exchange, state, result) }
            server.start()

            val redirectUri = "$LOOPBACK_SCHEME://$LOOPBACK_HOST:${server.address.port}"
            if (!openBrowser(buildAuthorizeUrl(provider, state, redirectUri))) {
                throw OAuthAuthenticationException("Could not open the browser for sign-in.")
            }

            return withTimeout(timeout) { result.await() }
        } catch (e: TimeoutCancellationException) {
            // Only the timeout is translated; genuine outer cancellation propagates untouched.
            throw OAuthAuthenticationException("Sign-in timed out. Please try again.", e)
        } finally {
            server.stop(0)
        }
    }

    private fun handleCallback(
        exchange: HttpExchange,
        expectedState: String,
        result: CompletableDeferred<String>,
    ) {
        try {
            val params = parseQuery(exchange.requestURI.rawQuery)
            val state = params["state"]

            // Only a request bearing the exact high-entropy state may drive the outcome. This
            // ignores favicon/preflight probes and, crucially, prevents another local process from
            // cancelling a legitimate in-flight sign-in with a wrong-state request — such requests
            // get the failure page but must NOT complete the deferred. Compared in constant time.
            if (state == null || !constantTimeEquals(state, expectedState)) {
                respond(exchange, FAILURE_HTML)
                return
            }

            val code = params["code"]
            when {
                params["error"] != null -> {
                    respond(exchange, FAILURE_HTML)
                    result.completeExceptionally(OAuthAuthenticationException("Sign-in was cancelled."))
                }

                code.isNullOrEmpty() -> {
                    respond(exchange, FAILURE_HTML)
                    result.completeExceptionally(
                        OAuthAuthenticationException("Sign-in did not return an authorization code."),
                    )
                }

                else -> {
                    // Write the response BEFORE completing: completing can immediately resume the
                    // coroutine and hit `server.stop(0)` in the finally, closing this exchange.
                    respond(exchange, SUCCESS_HTML)
                    result.complete(code)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle OAuth loopback callback", e)
            result.completeExceptionally(OAuthAuthenticationException(cause = e))
        }
    }

    private fun buildAuthorizeUrl(
        provider: OAuthProvider,
        state: String,
        redirectUri: String,
    ): String {
        val base = config.marketplaceBaseUrl.trimEnd('/')
        return "$base/auth/${provider.slug}/desktop?state=${encode(state)}&redirect=${encode(redirectUri)}"
    }

    private fun generateState(): String {
        val bytes = ByteArray(STATE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery
            .split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                when {
                    // A valueless key such as a bare `error` maps to an empty value (some providers
                    // send `?error` without `=`), so the error branch still fires.
                    idx < 0 -> decode(pair) to ""

                    // Malformed: empty key.
                    idx == 0 -> null

                    else -> decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
                }
            }.toMap()
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

    private fun respond(
        exchange: HttpExchange,
        html: String,
    ) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val LOOPBACK_SCHEME = "http"
        const val STATE_BYTES = 32
        const val HTTP_OK = 200
        val DEFAULT_TIMEOUT: Duration = 5.minutes

        val SUCCESS_HTML =
            """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><title>Cereal</title></head>
            <body style="font-family:sans-serif;text-align:center;padding-top:80px">
            <h2>You're signed in</h2><p>You can close this window and return to Cereal.</p>
            </body></html>
            """.trimIndent()

        val FAILURE_HTML =
            """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><title>Cereal</title></head>
            <body style="font-family:sans-serif;text-align:center;padding-top:80px">
            <h2>Sign-in was not completed</h2><p>You can close this window and return to Cereal to try again.</p>
            </body></html>
            """.trimIndent()
    }
}
