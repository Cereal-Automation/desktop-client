package com.cereal.client.infrastructure.data.datasource.network.llm

import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmToolChoice
import com.cereal.client.infrastructure.data.datasource.network.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * The HTTP behaviour every LLM datasource shares, so a datasource only maps its own request and
 * response JSON:
 * - one JSON POST, the response body back on 2xx; one `GET /models` read into [LlmModel]s;
 * - 408, 429 and 5xx retried after each of [retryDelays], then thrown;
 * - status mapping: 401/403 (and Gemini's 400 naming the API key) → [LlmCallException.Authentication], 429 → [LlmCallException.RateLimited]
 *   (with `retry-after`, seconds or HTTP-date), 408/5xx/network/timeout → [LlmCallException.Unavailable],
 *   any other 4xx → [LlmCallException.InvalidRequest] with the provider's message (and
 *   [LlmCallException.InvalidRequest.missingModel] set when the datasource recognises a model-not-found error).
 *
 * Privacy: request and response bodies and headers (the API key) never reach a log line or an
 * exception message; only the provider's own error message is passed on.
 */
class LlmHttp(
    private val client: OkHttpClient,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) {
    /** The `error` object both APIs send on a non-2xx: `{"error":{"type"?,"message"?,"code"?}}`. */
    @Serializable
    data class ErrorDetail(
        val type: String? = null,
        val message: String? = null,
        // A string on OpenAI, a number on some compatible servers.
        val code: JsonElement? = null,
    )

    /**
     * POSTs [body] to [url]. [isModelNotFound] tells from the provider's error whether [model] (the
     * configured one) doesn't exist.
     */
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        model: String,
        isModelNotFound: (ErrorDetail) -> Boolean,
    ): String =
        execute(request(url, headers).post(body.toRequestBody(JSON)).build()) { response ->
            val error = response.errorDetail()
            response.toFailure(error?.message, model.takeIf { error != null && isModelNotFound(error) })
        }

    /**
     * `GET [url]` for a `{"data":[{"id",...}]}` model list (entries read by [toChatModel]), with the same retries and status
     * mapping as [postJson], except that a 404 means the base URL is wrong:
     * [LlmCallException.InvalidRequest] with [NOT_FOUND_MESSAGE].
     */
    suspend fun getModels(
        url: String,
        headers: Map<String, String>,
    ): List<LlmModel> {
        val body =
            execute(request(url, headers).build()) { response ->
                if (response.code == HTTP_NOT_FOUND) {
                    LlmCallException.InvalidRequest(NOT_FOUND_MESSAGE)
                } else {
                    response.toFailure(response.errorDetail()?.message, null)
                }
            }
        return readResponse {
            json
                .parseToJsonElement(body)
                .jsonObject
                .getValue("data")
                .jsonArray
                .mapNotNull { (it as? JsonObject)?.toChatModel() }
        }
    }

    /**
     * One `/models` entry as an [LlmModel], or null when the entry says the model can't chat or has no string
     * `id` (it's skipped, not fatal). Any other field of the wrong shape counts as missing.
     */
    private fun JsonObject.toChatModel(): LlmModel? {
        val id = string("id")?.removePrefix("models/")?.takeIf { it.isNotBlank() } ?: return null
        val outputs = (this["architecture"] as? JsonObject)?.strings("output_modalities")
        val hidden =
            (outputs != null && outputs != listOf("text")) ||
                strings("endpoints")?.contains(CHAT_COMPLETIONS_ENDPOINT) == false ||
                strings("supported_parameters")?.contains("tools") == false
        if (hidden) return null
        val pricing = this["pricing"] as? JsonObject
        return LlmModel(
            id = id,
            displayName = string("display_name") ?: string("name"),
            created = created(),
            pricedFree = pricing != null && pricing.string("prompt") == "0" && pricing.string("completion") == "0",
        )
    }

    /** `created` in Unix seconds (OpenAI-compatible) or `created_at` in RFC 3339 (Anthropic); `0` is unknown. */
    private fun JsonObject.created(): Instant? {
        val seconds = (this["created"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        if (seconds != null && seconds > 0) return Instant.ofEpochSecond(seconds)
        return string("created_at")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String>? = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private fun request(
        url: String,
        headers: Map<String, String>,
    ) = Request.Builder().url(url).headers(headers.toHeaders())

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
            currentCoroutineContext()[LlmRetryListener]?.onRetry(failure, attempt + 1, retryDelays.size)
            // ponytail: fixed backoff, ignores retry-after; honour it (capped) if providers ask for long waits.
            delay(retryDelays[attempt++])
        }
    }

    /** Bodies may be any text, or the error object wrapped in an array (Gemini), so this never throws. */
    private fun Response.errorDetail(): ErrorDetail? =
        runCatching {
            val element = json.parseToJsonElement(body.string())
            json.decodeFromJsonElement<ErrorResponse>((element as? JsonArray)?.first() ?: element).error
        }.getOrNull()

    private fun Response.toFailure(
        providerMessage: String?,
        missingModel: String?,
    ): LlmCallException {
        // Gemini rejects a bad key with 400 INVALID_ARGUMENT "Please pass a valid API key".
        // ponytail: message match; switch to the error's `status` if Google rewords it.
        if (code == HTTP_BAD_REQUEST && providerMessage?.contains("api key", ignoreCase = true) == true) {
            return LlmCallException.Authentication()
        }
        return when (code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
                LlmCallException.Authentication()
            }

            HTTP_TOO_MANY_REQUESTS -> {
                LlmCallException.RateLimited(retryAfter = header("retry-after")?.let(::parseRetryAfter))
            }

            HTTP_CLIENT_TIMEOUT, in SERVER_ERRORS -> {
                LlmCallException.Unavailable()
            }

            else -> {
                LlmCallException.InvalidRequest(
                    message = providerMessage ?: "The AI provider rejected the request (HTTP $code).",
                    missingModel = missingModel,
                )
            }
        }
    }

    /** `retry-after` is either delay-seconds or an HTTP-date (RFC 9110 §10.2.3); anything else is ignored. */
    private fun parseRetryAfter(value: String): Duration? =
        value.trim().toLongOrNull()?.seconds
            ?: runCatching { ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }
                .getOrNull()
                ?.let {
                    java.time.Duration
                        .between(Instant.now(), it.toInstant())
                        .toKotlinDuration()
                        .coerceAtLeast(Duration.ZERO)
                }

    /**
     * Runs a datasource's parse of a 2xx body. Any failure (malformed JSON, missing or mistyped fields)
     * becomes [LlmCallException.Unavailable] without a cause: kotlinx serialization errors quote the
     * JSON, which is model output.
     */
    inline fun <T> readResponse(parse: () -> T): T =
        try {
            parse()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            throw LlmCallException.Unavailable("The AI provider sent a response Cereal couldn't read.")
        }

    /**
     * Reads an assistant turn's provider payload as this datasource's JSON shape [T]. A payload of another
     * shape was written by the other provider (the user switched provider mid-conversation):
     * [LlmCallException.InvalidRequest], nothing sent.
     */
    inline fun <reified T : JsonElement> payloadAs(payload: String): T =
        runCatching { json.parseToJsonElement(payload) }.getOrNull() as? T
            ?: throw LlmCallException.InvalidRequest(PROVIDER_SWITCHED_MESSAGE)

    @Serializable
    private data class ErrorResponse(
        val error: ErrorDetail,
    )

    companion object {
        /** Shared by the datasources: provider responses carry many fields we don't read. */
        val json = Json { ignoreUnknownKeys = true }
        val DEFAULT_RETRY_DELAYS = listOf(1.seconds, 2.seconds, 4.seconds)
        const val NOT_FOUND_MESSAGE = "Not found: there are no models at this base URL. Check it, including the version path (for example /v1)."
        const val PROVIDER_SWITCHED_MESSAGE =
            "This conversation has a reply from a different AI provider. Start a new conversation after changing the provider in Settings → AI."
        private const val CHAT_COMPLETIONS_ENDPOINT = "/api/v1/chat/completions"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val SERVER_ERRORS = 500..599
        private val JSON = "application/json".toMediaType()
        private val logger = LoggerFactory.getLogger(LlmHttp::class.java)
    }
}

/**
 * Hears about each retry of an LLM call made in this coroutine context: [onRetry] gets the failure being
 * retried, the retry number and the retry count. The script bridge installs one to tell the task log.
 */
class LlmRetryListener(
    val onRetry: (failure: LlmCallException, retry: Int, of: Int) -> Unit,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LlmRetryListener>
}

/** `tool_choice` as both APIs spell it. */
val LlmToolChoice.wireName: String
    get() =
        when (this) {
            LlmToolChoice.AUTO -> "auto"
            LlmToolChoice.NONE -> "none"
        }
