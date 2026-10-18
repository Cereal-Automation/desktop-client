package com.cereal.client.presentation.settings.ai

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.ai.ClearLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
import com.cereal.client.presentation.error.ErrorResolver
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Settings → AI's Test connection and model list, through the real interactors and HTTP backends with
 * `/models` served by MockWebServer (doc-shaped fixtures in `src/test/resources/llm`).
 */
class AiSettingsViewModelTest {
    private val server = MockWebServer()
    private val repository = InMemoryLlmProviderConfigRepository()
    private val llmProvider = llmProviderOverHttp()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun viewModel() =
        AiSettingsViewModel(
            scope = scope,
            dispatcherProvider = CoroutinesDispatcherProvider(Dispatchers.IO, Dispatchers.IO, Dispatchers.IO),
            getLlmProviderConfigInteractor = GetLlmProviderConfigInteractor(repository),
            saveLlmProviderConfigInteractor = SaveLlmProviderConfigInteractor(repository),
            clearLlmProviderConfigInteractor = ClearLlmProviderConfigInteractor(repository),
            listLlmModelsInteractor = ListLlmModelsInteractor(llmProvider),
            errorResolver = mockk(relaxed = true),
        )

    private fun await(condition: () -> Boolean) =
        runBlocking {
            withTimeout(5_000) { while (!condition()) delay(10) }
        }

    private fun fixture(path: String) = MockResponse().setBody(javaClass.getResource("/llm/$path")!!.readText())

    private fun AiSettingsViewModel.chooseAnthropicOnServer() {
        selectPreset(LlmPreset.ANTHROPIC)
        form.baseUrlState.text = server.url("/v1").toString()
        form.apiKeyState.text = "sk-ant-1"
    }

    @Test
    fun `a successful test reports the model count, fills the list and keeps the default model`() {
        server.enqueue(fixture("anthropic/models.json"))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Connected }
        assertEquals("Connected · 2 models", vm.connectionStatus.value.message)
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-4-5-20251001"), vm.form.models.value)
        assertEquals("claude-opus-5-5", vm.form.modelState.text)
    }

    @Test
    fun `a rejected key reports a bad key and empties the list`() {
        server.enqueue(fixture("anthropic/models.json"))
        server.enqueue(MockResponse().setResponseCode(401))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        vm.testConnection()
        await {
            vm.form.models.value
                .isNotEmpty()
        }

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals("Bad API key: the provider rejected it.", vm.connectionStatus.value.message)
        assertEquals(emptyList<String>(), vm.form.models.value)
    }

    @Test
    fun `an unreachable server reports unreachable`() {
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        server.shutdown()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals("Unreachable: check the base URL and your connection.", vm.connectionStatus.value.message)
    }

    @Test
    fun `a wrong base URL reports not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals(
            "Not found: there are no models at this base URL. Check it, including the version path (for example /v1).",
            vm.connectionStatus.value.message,
        )
    }

    @Test
    fun `testing without a required key marks the key field and calls nothing`() {
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        vm.form.apiKeyState.text = ""

        vm.testConnection()

        await { vm.form.apiKeyState.error != null }
        assertEquals(0, server.requestCount)
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
    }

    @Test
    fun `an OpenAI-compatible test works with no model entered yet`() {
        server.enqueue(fixture("openai/models.json"))
        val vm = viewModel()
        vm.selectPreset(LlmPreset.OTHER)
        vm.form.baseUrlState.text = server.url("/v1").toString()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Connected }
        assertEquals(listOf("gpt-5", "gpt-5-mini"), vm.form.models.value)
        assertEquals("", vm.form.modelState.text)
    }

    @Test
    fun `opening with a saved provider fetches the list silently`() {
        server.enqueue(fixture("openai/models.json"))
        runBlocking {
            repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url("/v1").toString(), "", "gpt-5"))
        }

        val vm = viewModel()

        await {
            vm.form.models.value
                .isNotEmpty()
        }
        assertEquals(listOf("gpt-5", "gpt-5-mini"), vm.form.models.value)
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
        assertEquals("gpt-5", vm.form.modelState.text)
    }

    @Test
    fun `a failed silent fetch shows nothing and save still works`() {
        server.enqueue(MockResponse().setResponseCode(401))
        runBlocking {
            repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url("/v1").toString(), "old", "gpt-5"))
        }
        val vm = viewModel()
        await { server.requestCount == 1 && vm.form.modelState.text == "gpt-5" }

        vm.form.modelState.text = "my-own-model"
        vm.save()

        await { runBlocking { repository.get() }?.model == "my-own-model" }
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
        assertEquals(emptyList<String>(), vm.form.models.value)
    }

    @Test
    fun `switching provider drops the fetched list and status`() {
        server.enqueue(fixture("anthropic/models.json"))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        vm.testConnection()
        await { vm.connectionStatus.value is ConnectionStatus.Connected }

        vm.selectPreset(LlmPreset.OTHER)

        assertEquals(emptyList<String>(), vm.form.models.value)
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
    }

    @Test
    fun `switching between presets of the same provider type clears the key, so it never reaches the other host`() {
        val vm = viewModel()
        vm.selectPreset(LlmPreset.OPENAI)
        vm.form.apiKeyState.text = "sk-openai-1"

        vm.selectPreset(LlmPreset.GEMINI)

        assertEquals("", vm.form.apiKeyState.text)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", vm.form.baseUrlState.text)
        assertEquals("gemini-2.5-flash", vm.form.modelState.text)
    }

    @Test
    fun `remove deletes the saved config and deselects the preset`() {
        runBlocking { repository.save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "k", "claude-opus-5-5")) }
        val vm = viewModel()
        await { vm.form.preset.value == LlmPreset.ANTHROPIC }

        vm.remove()

        await { runBlocking { repository.get() } == null }
        assertEquals(null, vm.form.preset.value)
    }
}
