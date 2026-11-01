package com.cereal.client.presentation.settings.ai

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.presentation.view.UiText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_connection_bad_key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** The form's automatic model-list load, over [InMemoryLlmProvider] in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmPresetFormStateTest {
    private val models = listOf(LlmModel("claude-opus-5-5"), LlmModel("claude-haiku-4-5"))
    private val provider = InMemoryLlmProvider(models = models)

    private fun TestScope.form(initial: LlmPreset? = LlmPreset.ANTHROPIC): LlmPresetFormState {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LlmPresetFormState(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher),
            listLlmModelsInteractor = ListLlmModelsInteractor(provider),
            initial = initial,
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the list loads about 500 ms after the key makes a valid connection, showing Loading meanwhile`() =
        runTest {
            val form = form()

            form.apiKeyState.onValueChange("sk-ant-1")
            advance(499)
            assertEquals(0, provider.listModelsRequests.size)
            assertEquals(ModelListState.Loading, form.modelList.value)

            advance(1)
            assertEquals(ModelListState.Loaded(models), form.modelList.value)
        }

    @Test
    fun `choosing Local shows Loading at once, since its default address is usable`() =
        runTest {
            val form = form(initial = null)

            form.select(LlmPreset.LOCAL)

            assertEquals(ModelListState.Loading, form.modelList.value)
        }

    @Test
    fun `nothing is fetched while the connection is not valid`() =
        runTest {
            val form = form()

            form.baseUrlState.onValueChange("not a url")
            form.apiKeyState.onValueChange("sk-ant-1")
            advance(1_000)

            assertEquals(ModelListState.Idle, form.modelList.value)
            assertEquals(0, provider.listModelsRequests.size)
        }

    @Test
    fun `typing during the pause starts it over and only the last key is used`() =
        runTest {
            val form = form()

            form.apiKeyState.onValueChange("sk-a")
            advance(300)
            form.apiKeyState.onValueChange("sk-ab")
            advance(300)
            assertEquals(0, provider.listModelsRequests.size)

            advance(200)
            assertEquals(listOf("sk-ab"), provider.listModelsRequests.map { it.apiKey?.reveal() })
            assertEquals(ModelListState.Loaded(models), form.modelList.value)
        }

    @Test
    fun `typing while a fetch runs cancels it`() =
        runTest {
            provider.listModelsLatencyMillis = 1_000
            val form = form()
            form.apiKeyState.onValueChange("sk-a")
            advance(500)
            assertEquals(ModelListState.Loading, form.modelList.value)

            form.apiKeyState.onValueChange("sk-ab")
            // The first fetch would have finished at 1500 ms; the second one finishes at 2000 ms.
            advance(1_200)
            assertEquals(ModelListState.Loading, form.modelList.value)

            advance(300)
            assertEquals(ModelListState.Loaded(models), form.modelList.value)
            assertEquals(listOf("sk-a", "sk-ab"), provider.listModelsRequests.map { it.apiKey?.reveal() })
        }

    @Test
    fun `editing the model does not fetch`() =
        runTest {
            val form = form()

            form.modelState.onValueChange("claude-haiku-4-5")
            advance(1_000)

            assertEquals(0, provider.listModelsRequests.size)
        }

    @Test
    fun `showing a saved config loads at once`() =
        runTest {
            val form = form(initial = null)

            form.show(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-opus-5-5"))
            runCurrent()

            assertEquals(ModelListState.Loaded(models), form.modelList.value)
            assertEquals("claude-opus-5-5", form.modelState.text)
        }

    @Test
    fun `a failed load reports the reason and Retry loads again`() =
        runTest {
            provider.listModelsError = LlmCallException.Authentication()
            val form = form()
            form.apiKeyState.onValueChange("bad")
            advance(500)
            assertEquals(ModelListState.Failed(UiText.Resource(Res.string.ai_connection_bad_key)), form.modelList.value)

            provider.listModelsError = null
            form.retry()
            runCurrent()

            assertEquals(ModelListState.Loaded(models), form.modelList.value)
        }

    @Test
    fun `switching preset clears the list and the selection`() =
        runTest {
            val form = form()
            form.apiKeyState.onValueChange("sk-ant-1")
            advance(500)
            form.modelState.onValueChange("claude-haiku-4-5")

            form.select(LlmPreset.OPENAI)

            assertEquals(ModelListState.Idle, form.modelList.value)
            assertEquals(emptyList<LlmModel>(), form.models)
            assertEquals("gpt-5", form.modelState.text)
            assertEquals("", form.apiKeyState.text)
        }

    @Test
    fun `switching preset cancels a pending load`() =
        runTest {
            val form = form()
            form.apiKeyState.onValueChange("sk-ant-1")
            advance(300)

            form.select(LlmPreset.OPENAI)
            advance(1_000)

            assertEquals(0, provider.listModelsRequests.size)
            assertEquals(ModelListState.Idle, form.modelList.value)
        }

    private fun TestScope.loadedIds(vararg listed: LlmModel): List<String> {
        provider.models = listed.toList()
        val form = form(initial = null)
        form.show(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "new"))
        runCurrent()
        return form.models.map { it.id }
    }

    @Test
    fun `the list is newest first with undated models last in API order`() =
        runTest {
            val ids =
                loadedIds(
                    LlmModel("undated-1"),
                    LlmModel("old", created = Instant.parse("2025-01-01T00:00:00Z")),
                    LlmModel("undated-2"),
                    LlmModel("new", created = Instant.parse("2026-01-01T00:00:00Z")),
                )

            assertEquals(listOf("new", "old", "undated-1", "undated-2"), ids)
        }

    @Test
    fun `a list without any dates keeps the API order`() =
        runTest {
            assertEquals(listOf("b", "a", "c"), loadedIds(LlmModel("b"), LlmModel("a"), LlmModel("c")))
        }

    private val old = Instant.parse("2025-01-01T00:00:00Z")
    private val mid = Instant.parse("2025-06-01T00:00:00Z")
    private val new = Instant.parse("2026-01-01T00:00:00Z")

    /** Sets up [preset] from scratch, lets [listed] load, and returns the model the form then holds. */
    private fun TestScope.defaultModel(
        preset: LlmPreset,
        vararg listed: LlmModel,
    ): String {
        provider.models = listed.toList()
        val form = form(initial = preset)
        if (preset == LlmPreset.OTHER) form.baseUrlState.onValueChange("https://llm.example.com/v1")
        form.apiKeyState.onValueChange("key-1")
        advance(500)
        return form.modelState.text
    }

    @Test
    fun `OpenAI defaults to the preset's model when listed, even with newer ones`() =
        runTest {
            assertEquals("gpt-5", defaultModel(LlmPreset.OPENAI, LlmModel("gpt-5", created = old), LlmModel("gpt-6", created = new)))
        }

    @Test
    fun `Gemini defaults to the preset's model in its dateless list`() =
        runTest {
            assertEquals("gemini-3.8-flash", defaultModel(LlmPreset.GEMINI, LlmModel("gemini-4-pro"), LlmModel("gemini-3.8-flash")))
        }

    @Test
    fun `Gemini without the preset's model in a dateless list leaves it to the user`() =
        runTest {
            assertEquals("", defaultModel(LlmPreset.GEMINI, LlmModel("gemini-flash-latest"), LlmModel("gemini-4-pro")))
        }

    @Test
    fun `Anthropic defaults to the newest model`() =
        runTest {
            assertEquals(
                "claude-opus-6",
                defaultModel(LlmPreset.ANTHROPIC, LlmModel("claude-opus-5-5", created = old), LlmModel("claude-opus-6", created = new)),
            )
        }

    @Test
    fun `OpenRouter prefers the newest free model and never a router`() =
        runTest {
            assertEquals(
                "meta/free-mid",
                defaultModel(
                    LlmPreset.OPENROUTER,
                    LlmModel("openrouter/free", created = new, pricedFree = true),
                    LlmModel("paid-new", created = new),
                    LlmModel("meta/free-old", created = old, pricedFree = true),
                    LlmModel("meta/free-mid", created = mid, pricedFree = true),
                ),
            )
        }

    @Test
    fun `without a free model the newest non-router model is chosen`() =
        runTest {
            assertEquals(
                "paid-mid",
                defaultModel(
                    LlmPreset.OPENROUTER,
                    LlmModel("openrouter/auto", created = new),
                    LlmModel("paid-old", created = old),
                    LlmModel("paid-mid", created = mid),
                ),
            )
        }

    @Test
    fun `Local defaults to the newest model`() =
        runTest {
            assertEquals("qwen4", defaultModel(LlmPreset.LOCAL, LlmModel("llama5", created = old), LlmModel("qwen4", created = new)))
        }

    @Test
    fun `Other without dates leaves it to the user`() =
        runTest {
            assertEquals("", defaultModel(LlmPreset.OTHER, LlmModel("a"), LlmModel("b")))
        }

    @Test
    fun `a saved model is never replaced by a reload`() =
        runTest {
            provider.models = listOf(LlmModel("claude-opus-6", created = new))
            val form = form(initial = null)
            form.show(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-haiku-4-5"))
            runCurrent()
            form.apiKeyState.onValueChange("sk-ant-2")
            advance(500)

            assertEquals("claude-haiku-4-5", form.modelState.text)
        }

    @Test
    fun `a model the user picked is never replaced by a reload`() =
        runTest {
            provider.models = listOf(LlmModel("claude-opus-6", created = new), LlmModel("claude-haiku-4-5", created = old))
            val form = form()
            form.apiKeyState.onValueChange("sk-ant-1")
            advance(500)
            form.modelState.onValueChange("claude-haiku-4-5")

            form.retry()
            runCurrent()

            assertEquals("claude-haiku-4-5", form.modelState.text)
        }
}
