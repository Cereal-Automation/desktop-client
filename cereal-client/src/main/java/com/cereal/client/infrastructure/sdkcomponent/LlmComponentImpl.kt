package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmCompletion
import com.cereal.client.domain.model.llm.LlmCompletionRequest
import com.cereal.client.domain.model.llm.LlmFinishReason
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmToolCall
import com.cereal.client.domain.model.llm.LlmToolChoice
import com.cereal.client.domain.model.llm.LlmToolDefinition
import com.cereal.client.domain.model.llm.LlmUserPart
import com.cereal.sdk.component.llm.CerealPlatformApi
import com.cereal.sdk.component.llm.LlmComponent
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmResponse
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.LlmUsage
import com.cereal.sdk.component.llm.StopReason
import com.cereal.sdk.component.llm.ToolChoice
import com.cereal.sdk.component.llm.UserPart
import com.cereal.sdk.component.llm.LlmToolCall as SdkLlmToolCall

/**
 * The script-facing `provider.llm()`: maps SDK types to the client's [LlmCompletionRequest] and back,
 * and each [LlmCallException] to the matching SDK [LlmException]. Cancellation and unexpected errors
 * pass through untouched.
 */
@OptIn(CerealPlatformApi::class)
class LlmComponentImpl(
    private val completeLlmRequestInteractor: CompleteLlmRequestInteractor,
) : LlmComponent {
    override suspend fun complete(request: LlmRequest): LlmResponse {
        val completion =
            try {
                completeLlmRequestInteractor.run(CompleteLlmRequestInteractor.Params(request.toDomain()))
            } catch (e: LlmCallException) {
                throw e.toSdk()
            }
        return completion.toSdk()
    }

    private fun LlmRequest.toDomain() =
        LlmCompletionRequest(
            system = system,
            messages = turns.map { it.toDomain() },
            tools = tools.map { LlmToolDefinition(it.name, it.description, it.inputSchemaJson) },
            toolChoice =
                when (toolChoice) {
                    ToolChoice.Auto -> LlmToolChoice.AUTO
                    ToolChoice.None -> LlmToolChoice.NONE
                },
            responseSchemaJson = responseSchemaJson,
            maxTokens = maxTokens,
        )

    private fun LlmTurn.toDomain(): LlmMessage =
        when (this) {
            is LlmTurn.User -> LlmMessage.User(parts.map { it.toDomain() })
            is LlmTurn.Assistant -> LlmMessage.Assistant(text, toolCalls.map { it.toDomain() }, providerPayload)
        }

    private fun UserPart.toDomain(): LlmUserPart =
        when (this) {
            is UserPart.Text -> LlmUserPart.Text(text)
            is UserPart.Image -> LlmUserPart.Image(bytes, mediaType.mimeType)
            is UserPart.ToolResult -> LlmUserPart.ToolResult(toolCallId, text, isError)
        }

    private fun SdkLlmToolCall.toDomain() = LlmToolCall(id, name, argumentsJson)

    private fun LlmToolCall.toSdk() = SdkLlmToolCall(id, name, argumentsJson)

    private fun LlmCompletion.toSdk() =
        LlmResponse(
            text = text,
            toolCalls = toolCalls.map { it.toSdk() },
            structuredJson = structuredJson,
            stopReason =
                when (finishReason) {
                    LlmFinishReason.END_TURN -> StopReason.EndTurn
                    LlmFinishReason.TOOL_USE -> StopReason.ToolUse
                    LlmFinishReason.MAX_TOKENS -> StopReason.MaxTokens
                    LlmFinishReason.REFUSAL -> StopReason.Refusal
                },
            usage = LlmUsage(usage.inputUncached, usage.cacheRead, usage.cacheWrite, usage.output),
            model = model,
            providerPayload = providerPayload,
        )

    private fun LlmCallException.toSdk(): LlmException =
        when (this) {
            is LlmCallException.NotConfigured -> {
                LlmException.NotConfigured(message)
            }

            is LlmCallException.Authentication -> {
                LlmException.Authentication(message, this)
            }

            is LlmCallException.RateLimited -> {
                LlmException.RateLimited(retryAfter, message, this)
            }

            is LlmCallException.InvalidRequest -> {
                LlmException.InvalidRequest(message, this)
            }

            is LlmCallException.InvalidOutput -> {
                LlmException.InvalidOutput(
                    raw = raw,
                    assistantTurn =
                        LlmTurn.Assistant(
                            assistantMessage.text,
                            assistantMessage.toolCalls.map { it.toSdk() },
                            assistantMessage.providerPayload,
                        ),
                    message = message,
                )
            }

            is LlmCallException.Unavailable -> {
                LlmException.Unavailable(message, this)
            }
        }
}
