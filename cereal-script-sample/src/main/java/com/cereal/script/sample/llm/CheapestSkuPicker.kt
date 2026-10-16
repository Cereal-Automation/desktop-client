package com.cereal.script.sample.llm

import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmTool
import com.cereal.sdk.component.llm.LlmToolCall
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.StopReason
import com.cereal.sdk.component.llm.UserPart

/**
 * Asks the model the user set up in Settings → AI which SKU is cheapest. The model looks prices up through the
 * `get_price` tool, which this script runs, and ends with a structured answer. With no AI provider set up, or no
 * usable answer, it falls back to the lowest price. Other [LlmException]s (bad key, rate limit, invalid output, ...)
 * propagate and fail the task with their message.
 *
 * Scripts that call `provider.llm()` must set `"sdk_version": "1.14.0"` (or higher) in manifest.json.
 */
class CheapestSkuPicker(
    private val prices: Map<String, Double>,
) {
    data class Pick(
        val sku: String,
        val reason: String,
        val byModel: Boolean,
    )

    suspend fun pick(provider: ComponentProvider): Pick {
        val logger = provider.logger()
        var request =
            LlmRequest(
                system = "You compare product prices. Look every price up with get_price before answering.",
                turns = listOf(LlmTurn.User(listOf(UserPart.Text("Which of these SKUs is cheapest: ${prices.keys.joinToString()}?")))),
                tools = listOf(GET_PRICE),
                // Tools and the response schema go on every call; the model answers in the schema once it's done.
                responseSchemaJson = ANSWER_SCHEMA,
            )
        try {
            // The script drives the loop: each complete() is exactly one model call. Bound it to bound the cost.
            repeat(MAX_CALLS) {
                val response = provider.llm().complete(request)
                if (response.stopReason != StopReason.ToolUse) {
                    val json = response.structuredJson ?: return fallback("the model gave no answer: ${response.stopReason}")
                    return Pick(stringField(json, "sku"), stringField(json, "reason"), byModel = true)
                }
                // Answer every tool call of this turn in one user turn, after the model's own turn.
                val results = response.toolCalls.map(::runTool)
                request = request.copy(turns = request.turns + response.assistantTurn + LlmTurn.User(results))
            }
            return fallback("the model kept calling tools")
        } catch (e: LlmException.NotConfigured) {
            logger.warn("${e.message} Picking the lowest price without AI.")
            return fallback("no AI provider set up")
        }
    }

    private fun runTool(call: LlmToolCall): UserPart.ToolResult {
        val sku = stringField(call.argumentsJson, "sku")
        val price = prices[sku] ?: return UserPart.ToolResult(call.id, "Unknown SKU $sku.", isError = true)
        return UserPart.ToolResult(call.id, price.toString())
    }

    private fun fallback(why: String): Pick = Pick(prices.minBy { it.value }.key, "Lowest price ($why).", byModel = false)

    private companion object {
        const val MAX_CALLS = 5

        val GET_PRICE =
            LlmTool(
                name = "get_price",
                description = "Returns the current price of a SKU.",
                inputSchemaJson =
                    """{"type":"object","properties":{"sku":{"type":"string"}},"required":["sku"],"additionalProperties":false}""",
            )

        // Stay within Anthropic's JSON Schema subset: additionalProperties false, no numeric or length constraints.
        const val ANSWER_SCHEMA =
            """{"type":"object","properties":{"sku":{"type":"string"},"reason":{"type":"string"}},""" +
                """"required":["sku","reason"],"additionalProperties":false}"""

        // ponytail: regex read of one flat string field, escapes left as is. The client already validated the JSON
        // against the schema; a real script with nested JSON would use the JSON library of its choice.
        fun stringField(
            json: String,
            name: String,
        ): String =
            Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)?.groupValues?.get(1)
                ?: error("Field $name missing from validated JSON.")
    }
}
