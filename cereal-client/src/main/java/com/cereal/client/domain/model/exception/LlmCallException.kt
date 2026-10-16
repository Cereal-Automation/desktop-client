package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.llm.LlmMessage
import kotlin.time.Duration

/**
 * Expected failures of an LLM call. [com.cereal.client.domain.provider.LlmProvider] backends throw these
 * (never raw HTTP/IO errors); the SDK bridge maps each to the matching script-facing `LlmException`.
 * Messages are shown to the user and must never contain the API key, prompts or model output.
 */
sealed class LlmCallException(
    message: String,
    cause: Throwable? = null,
) : CerealException(message, cause) {
    class NotConfigured : LlmCallException("No AI provider is set up. Choose one in Settings → AI.")

    /** 401/403: the key was rejected. */
    class Authentication(
        message: String = "The AI provider rejected the API key. Check it in Settings → AI.",
        cause: Throwable? = null,
    ) : LlmCallException(message, cause)

    /** 429 after the backend's retries ran out. */
    class RateLimited(
        val retryAfter: Duration? = null,
        message: String = "The AI provider is rate limiting requests. Try again later.",
        cause: Throwable? = null,
    ) : LlmCallException(message, cause)

    /**
     * 400/404/413/422: [message] is the provider's own explanation. [missingModel] is the configured
     * model when the provider said it doesn't exist (retired or removed); there is no fallback model.
     */
    class InvalidRequest(
        message: String,
        cause: Throwable? = null,
        val missingModel: String? = null,
    ) : LlmCallException(message, cause)

    /** The model's JSON failed the request's schema; [raw] and [assistantMessage] let the script retry. */
    class InvalidOutput(
        val raw: String,
        val assistantMessage: LlmMessage.Assistant,
        message: String = "The model's output doesn't match the requested schema.",
    ) : LlmCallException(message)

    /** 5xx, network errors and timeouts after the backend's retries ran out. */
    class Unavailable(
        message: String = "The AI provider can't be reached right now. Try again later.",
        cause: Throwable? = null,
    ) : LlmCallException(message, cause)
}
