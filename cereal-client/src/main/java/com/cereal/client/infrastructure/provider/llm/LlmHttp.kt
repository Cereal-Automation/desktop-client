package com.cereal.client.infrastructure.provider.llm

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.infrastructure.data.datasource.network.util.await
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The HTTP behaviour every LLM backend shares, so a backend only maps its own request and response
 * JSON:
 * - one JSON POST, the response body back on 2xx;
 * - 429 and 5xx retried after each of [retryDelays], then thrown;
 * - status mapping: 401/403 → [LlmCallException.Authentication], 429 → [LlmCallException.RateLimited]
 *   (with `retry-after`), 5xx/network/timeout → [LlmCallException.Unavailable], any other 4xx →
 *   [LlmCallException.InvalidRequest] with the provider's message (and [LlmCallException.InvalidRequest.missingModel]
 *   set when the backend recognises a model-not-found error).
 *
 * Privacy: request and response bodies and headers (the API key) never reach a log line or an
 * exception message; only the provider's own error message is passed on.
 */
class LlmHttp(
    private val client: OkHttpClient,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) {
    /** What a backend reads from a non-2xx body: the provider's message and whether it means "unknown model". */
    data class ProviderError(
        val message: String?,
        val modelNotFound: Boolean = false,
    )

    /**
     * POSTs [body] to [url]. [parseError] reads the provider's error body (it may get any text and must
     * not throw); [model] is the configured model, named when the provider reports it missing.
     */
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        model: String,
        parseError: (String) -> ProviderError,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .post(body.toRequestBody(JSON))
                .build()
        return execute(request) { it.toFailure(model, parseError) }
    }

    /**
     * GETs [url] (e.g. `{baseUrl}/models`), with the same retries and status mapping as [postJson],
     * except that a 404 means the base URL is wrong: [LlmCallException.InvalidRequest] with
     * [NOT_FOUND_MESSAGE].
     */
    suspend fun getJson(
        url: String,
        headers: Map<String, String>,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
        return execute(request) { response ->
            if (response.code == HTTP_NOT_FOUND) {
                LlmCallException.InvalidRequest(NOT_FOUND_MESSAGE)
            } else {
                response.toFailure(model = "") { ProviderError(null) }
            }
        }
    }

    private suspend fun execute(
        request: Request,
        toFailure: (Response) -> LlmCallException,
    ): String {
        var attempt = 0
        while (true) {
            val failure =
                try {
                    client.newCall(request).await().use { response ->
                        if (response.isSuccessful) return response.body.string()
                        toFailure(response)
                    }
                } catch (_: IOException) {
                    throw LlmCallException.Unavailable()
                }
            val retryable = failure is LlmCallException.RateLimited || failure is LlmCallException.Unavailable
            if (!retryable || attempt == retryDelays.size) throw failure
            logger.warn("LLM call failed ({}), retrying ({}/{})", failure::class.simpleName, attempt + 1, retryDelays.size)
            // ponytail: fixed backoff, ignores retry-after; honour it (capped) if providers ask for long waits.
            delay(retryDelays[attempt++])
        }
    }

    private fun Response.toFailure(
        model: String,
        parseError: (String) -> ProviderError,
    ): LlmCallException =
        when (code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
                LlmCallException.Authentication()
            }

            HTTP_TOO_MANY_REQUESTS -> {
                LlmCallException.RateLimited(retryAfter = header("retry-after")?.toLongOrNull()?.seconds)
            }

            in SERVER_ERRORS -> {
                LlmCallException.Unavailable()
            }

            else -> {
                val error = parseError(body.string())
                LlmCallException.InvalidRequest(
                    message = error.message ?: "The AI provider rejected the request (HTTP $code).",
                    missingModel = model.takeIf { error.modelNotFound },
                )
            }
        }

    /**
     * Runs a backend's parse of a 2xx body. A body it can't read becomes [LlmCallException.Unavailable]
     * without a cause: kotlinx serialization errors quote the JSON, which is model output.
     */
    inline fun <T> readResponse(parse: () -> T): T =
        try {
            parse()
        } catch (_: IllegalArgumentException) {
            // SerializationException and missing/mistyped fields
            throw LlmCallException.Unavailable("The AI provider sent a response Cereal couldn't read.")
        } catch (_: IllegalStateException) {
            throw LlmCallException.Unavailable("The AI provider sent a response Cereal couldn't read.")
        }

    companion object {
        val DEFAULT_RETRY_DELAYS = listOf(1.seconds, 2.seconds, 4.seconds)
        const val NOT_FOUND_MESSAGE = "Not found: there are no models at this base URL. Check it, including the version path (for example /v1)."
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERRORS = 500..599
        private val JSON = "application/json".toMediaType()
        private val logger = LoggerFactory.getLogger(LlmHttp::class.java)
    }
}
