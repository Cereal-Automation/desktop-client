package com.cereal.client.presentation.settings.ai

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.ai.ClearLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.feedback.FeedbackAction
import com.cereal.client.presentation.view.UiText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_connection_bad_key
import com.cereal_automation.cereal_client.generated.resources.ai_connection_unreachable
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
import java.time.Instant

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

    private fun viewModel(llmProvider: LlmProvider = this.llmProvider) =
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
    fun `a successful test reports the model count and picks no model`() {
        server.enqueue(fixture("anthropic/models.json"))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Connected }
        assertEquals(ConnectionStatus.Connected(2), vm.connectionStatus.value)
        assertEquals("", vm.form.modelState.text)
    }

    @Test
    fun `a rejected key reports a bad key`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals(ConnectionStatus.Failed(UiText.Resource(Res.string.ai_connection_bad_key)), vm.connectionStatus.value)
    }

    @Test
    fun `an unreachable server reports unreachable`() {
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        server.shutdown()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals(ConnectionStatus.Failed(UiText.Resource(Res.string.ai_connection_unreachable)), vm.connectionStatus.value)
    }

    @Test
    fun `a wrong base URL reports not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Failed }
        assertEquals(
            ConnectionStatus.Failed(
                UiText.Raw("Not found: there are no models at this base URL. Check it, including the version path (for example /v1)."),
            ),
            vm.connectionStatus.value,
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
        assertEquals(ConnectionStatus.Connected(2), vm.connectionStatus.value)
        assertEquals("", vm.form.modelState.text)
    }

    @Test
    fun `opening with a saved provider loads the list without a status`() {
        server.enqueue(fixture("openai/models.json"))
        runBlocking {
            repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url("/v1").toString(), "", "gpt-5"))
        }

        val vm = viewModel()

        await { vm.form.modelList.value is ModelListState.Loaded }
        assertEquals(listOf("gpt-5", "gpt-5-mini"), vm.form.models.map { it.id })
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
        assertEquals("gpt-5", vm.form.modelState.text)
    }

    @Test
    fun `a failed load on open shows no status and save still works`() {
        server.enqueue(MockResponse().setResponseCode(401))
        runBlocking {
            repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, server.url("/v1").toString(), "old", "gpt-5"))
        }
        val vm = viewModel()
        await { vm.form.modelList.value is ModelListState.Failed }

        vm.form.modelState.text = "my-own-model"
        vm.save()

        await { runBlocking { repository.get() }?.model == "my-own-model" }
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
    }

    @Test
    fun `switching provider drops the fetched list and status`() {
        server.enqueue(fixture("anthropic/models.json"))
        val vm = viewModel()
        vm.chooseAnthropicOnServer()
        vm.testConnection()
        await { vm.connectionStatus.value is ConnectionStatus.Connected }

        vm.selectPreset(LlmPreset.OTHER)

        assertEquals(ModelListState.Idle, vm.form.modelList.value)
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
        assertEquals("gemini-3.8-flash", vm.form.modelState.text)
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

    @Test
    fun `Test connection reports status but leaves the loaded list alone`() {
        val provider = InMemoryLlmProvider(models = listOf(LlmModel("claude-opus-5-5")))
        runBlocking { repository.save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "k", "claude-opus-5-5")) }
        val vm = viewModel(provider)
        await { vm.form.modelList.value is ModelListState.Loaded }
        provider.models = listOf(LlmModel("claude-opus-5-5"), LlmModel("claude-haiku-4-5"))

        vm.testConnection()

        await { vm.connectionStatus.value is ConnectionStatus.Connected }
        assertEquals(ConnectionStatus.Connected(2), vm.connectionStatus.value)
        assertEquals(listOf("claude-opus-5-5"), vm.form.models.map { it.id })
    }

    @Test
    fun `typing a key loads the list on its own`() {
        val provider = InMemoryLlmProvider(models = listOf(LlmModel("claude-opus-5-5")))
        val vm = viewModel(provider)
        vm.selectPreset(LlmPreset.ANTHROPIC)

        vm.form.apiKeyState.onValueChange("sk-ant-1")

        await { vm.form.modelList.value is ModelListState.Loaded }
        assertEquals(listOf("sk-ant-1"), provider.listModelsRequests.map { it.apiKey?.reveal() })
        assertEquals(ConnectionStatus.Idle, vm.connectionStatus.value)
    }

    @Test
    fun `Retry after a failed load on open fills the list`() {
        val provider = InMemoryLlmProvider(models = listOf(LlmModel("claude-opus-5-5")), listModelsError = LlmCallException.Unavailable())
        runBlocking { repository.save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "k", "claude-opus-5-5")) }
        val vm = viewModel(provider)
        await { vm.form.modelList.value is ModelListState.Failed }

        provider.listModelsError = null
        vm.form.retry()

        await { vm.form.modelList.value is ModelListState.Loaded }
        assertEquals("claude-opus-5-5", vm.form.modelState.text)
    }

    @Test
    fun `a saved default model is kept when a later key edit reloads the list`() {
        val provider = InMemoryLlmProvider(models = listOf(LlmModel("claude-opus-5-5", created = Instant.parse("2025-01-01T00:00:00Z"))))
        val vm = viewModel(provider)
        vm.selectPreset(LlmPreset.ANTHROPIC)
        vm.form.apiKeyState.onValueChange("sk-ant-1")
        await { vm.form.modelState.text == "claude-opus-5-5" }
        vm.save()
        await { runBlocking { repository.get() } != null && vm.feedbackAction.value != FeedbackAction.None }

        provider.models = listOf(LlmModel("claude-opus-6", created = Instant.parse("2026-01-01T00:00:00Z"))) + provider.models
        vm.form.apiKeyState.onValueChange("sk-ant-2")

        await { provider.listModelsRequests.size == 2 && vm.form.modelList.value is ModelListState.Loaded }
        assertEquals("claude-opus-5-5", vm.form.modelState.text)
    }
}
