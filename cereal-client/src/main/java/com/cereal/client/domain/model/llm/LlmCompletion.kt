package com.cereal.client.domain.model.llm

/**
 * One model call in client-owned terms, independent of the SDK's script-facing types and of any
 * provider's wire format. Backends of [com.cereal.client.domain.provider.LlmProvider] translate it to
 * their HTTP request. JSON (schemas, tool arguments) stays a plain JSON string.
 */
data class LlmCompletionRequest(
    val system: String?,
    val messages: List<LlmMessage>,
    val tools: List<LlmToolDefinition>,
    val toolChoice: LlmToolChoice,
    val responseSchemaJson: String?,
    val maxTokens: Int,
)

sealed interface LlmMessage {
    data class User(
        val parts: List<LlmUserPart>,
    ) : LlmMessage

    /**
     * A turn the model produced earlier, sent back as is. [providerPayload] is the backend's own opaque
     * data (thinking blocks, thought signatures) and must be restored verbatim; it is never logged.
     */
    data class Assistant(
        val text: String,
        val toolCalls: List<LlmToolCall>,
        val providerPayload: String?,
    ) : LlmMessage {
        override fun toString(): String = "Assistant(text=<${text.length} chars>, toolCalls=${toolCalls.size})"
    }
}

sealed interface LlmUserPart {
    data class Text(
        val text: String,
    ) : LlmUserPart

    /** Raw image bytes; backends base64-encode them. [mimeType] is one of `image/jpeg|png|gif|webp`. */
    class Image(
        val bytes: ByteArray,
        val mimeType: String,
    ) : LlmUserPart {
        override fun equals(other: Any?): Boolean = other is Image && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()

        override fun toString(): String = "Image(${bytes.size} bytes, $mimeType)"
    }

    data class ToolResult(
        val toolCallId: String,
        val text: String,
        val isError: Boolean,
    ) : LlmUserPart
}

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/** A tool call the model made. [argumentsJson] is a plain JSON object string (never double-encoded). */
data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

enum class LlmToolChoice { AUTO, NONE }

/** The model's answer, normalized across providers. */
data class LlmCompletion(
    val text: String,
    val toolCalls: List<LlmToolCall>,
    /** Set only when a response schema was requested and [finishReason] is [LlmFinishReason.END_TURN]. */
    val structuredJson: String?,
    val finishReason: LlmFinishReason,
    val usage: LlmTokenUsage,
    /** The model that actually answered, as the provider reports it. */
    val model: String,
    /** Opaque backend data to round-trip on the next call; see [LlmMessage.Assistant.providerPayload]. */
    val providerPayload: String?,
) {
    override fun toString(): String = "LlmCompletion(finishReason=$finishReason, usage=$usage, model=$model)"
}

enum class LlmFinishReason { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL }

/** Token counts with cache tokens split out of the input on every provider. */
data class LlmTokenUsage(
    val inputUncached: Int,
    val cacheRead: Int,
    val cacheWrite: Int,
    val output: Int,
)

/** A model a provider offers, from its `/models` list. */
data class LlmModel(
    val id: String,
    val displayName: String? = null,
)
