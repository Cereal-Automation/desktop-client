import com.cereal.script.sample.llm.CheapestSkuPicker
import com.cereal.sdk.component.llm.LlmToolCall
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.StopReason
import com.cereal.sdk.component.llm.UserPart
import com.cereal.test.components.TestComponentProviderFactory
import com.cereal.test.components.llmResponse
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class TestCheapestSkuPicker {
    private val prices = mapOf("ABC-1" to 19.99, "ABC-2" to 24.50)
    private val factory = TestComponentProviderFactory()

    @Test
    fun `falls back to the local minimum when no AI provider is set up`() =
        runBlocking {
            // The fake's default throws NotConfigured, like a user who never opened Settings → AI.
            val pick = CheapestSkuPicker(prices).pick(factory.create())

            assertEquals(CheapestSkuPicker.Pick("ABC-1", "Lowest price (no AI provider set up).", byModel = false), pick)
            assertEquals(1, factory.llm.requests.size)
        }

    @Test
    fun `runs the tool loop and returns the model's structured answer`() =
        runBlocking {
            val toolUse =
                llmResponse(
                    text = "Let me check both prices.",
                    toolCalls =
                        listOf(
                            LlmToolCall("call_1", "get_price", """{"sku":"ABC-1"}"""),
                            LlmToolCall("call_2", "get_price", """{"sku": "ABC-2"}"""),
                        ),
                    stopReason = StopReason.ToolUse,
                )
            val answer = """{"sku":"ABC-1","reason":"19.99 is less than 24.50."}"""
            factory.llm.queue(toolUse, llmResponse(text = answer, structuredJson = answer))

            val pick = CheapestSkuPicker(prices).pick(factory.create())

            assertEquals(CheapestSkuPicker.Pick("ABC-1", "19.99 is less than 24.50.", byModel = true), pick)
            val (first, second) = factory.llm.requests
            assertEquals(listOf("get_price"), first.tools.map { it.name })
            assertEquals(first.tools, second.tools)
            assertEquals(first.responseSchemaJson, second.responseSchemaJson)
            assertEquals(
                first.turns +
                    toolUse.assistantTurn +
                    LlmTurn.User(listOf(UserPart.ToolResult("call_1", "19.99"), UserPart.ToolResult("call_2", "24.5"))),
                second.turns,
            )
        }

    @Test
    fun `reports an unknown SKU to the model as a tool error`() =
        runBlocking {
            factory.llm.queue(
                llmResponse(toolCalls = listOf(LlmToolCall("call_1", "get_price", """{"sku":"NOPE"}""")), stopReason = StopReason.ToolUse),
                llmResponse(text = "{}", structuredJson = """{"sku":"ABC-1","reason":"Only known SKU."}"""),
            )

            CheapestSkuPicker(prices).pick(factory.create())

            assertEquals(
                LlmTurn.User(listOf(UserPart.ToolResult("call_1", "Unknown SKU NOPE.", isError = true))),
                factory.llm.requests[1]
                    .turns
                    .last(),
            )
        }

    @Test
    fun `falls back when the model refuses`() =
        runBlocking {
            factory.llm.queue(llmResponse(stopReason = StopReason.Refusal))

            val pick = CheapestSkuPicker(prices).pick(factory.create())

            assertEquals(CheapestSkuPicker.Pick("ABC-1", "Lowest price (the model gave no answer: Refusal).", byModel = false), pick)
        }
}
