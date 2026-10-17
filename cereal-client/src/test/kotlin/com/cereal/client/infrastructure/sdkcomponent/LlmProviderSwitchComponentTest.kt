package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.fixtures.llmProviderOverHttp
import com.cereal.client.infrastructure.sdkcomponent.fixtures.RecordingLoggerComponent
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.UserPart
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The user switches provider in Settings → AI while a script holds an assistant turn from the old one:
 * the next call fails as InvalidRequest, sending nothing.
 */
class LlmProviderSwitchComponentTest {
    private val server = MockWebServer()
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val component =
        LlmComponentImpl(
            CompleteLlmRequestInteractor(configRepository, llmProviderOverHttp()),
            RecordingLoggerComponent(),
        )

    private val question = LlmTurn.User(listOf(UserPart.Text("What is the capital of France?")))
    private val followUp = LlmTurn.User(listOf(UserPart.Text("And of Spain?")))

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `an Anthropic assistant turn sent to an OpenAI-compatible provider is InvalidRequest`() =
        runTest {
            assertSwitchFails(from = LlmProviderType.ANTHROPIC, fixture = "/llm/anthropic/messages-text.json", to = LlmProviderType.OPENAI_COMPATIBLE)
        }

    @Test
    fun `an OpenAI-compatible assistant turn sent to Anthropic is InvalidRequest`() =
        runTest {
            assertSwitchFails(from = LlmProviderType.OPENAI_COMPATIBLE, fixture = "/llm/openai/chat-text.json", to = LlmProviderType.ANTHROPIC)
        }

    private suspend fun assertSwitchFails(
        from: LlmProviderType,
        fixture: String,
        to: LlmProviderType,
    ) {
        configure(from)
        server.enqueue(MockResponse().setBody(javaClass.getResource(fixture)!!.readText()))
        val first = component.complete(LlmRequest(turns = listOf(question)))

        configure(to)
        val error =
            assertThrows<LlmException.InvalidRequest> {
                component.complete(LlmRequest(turns = listOf(question, first.assistantTurn, followUp)))
            }

        assertEquals(PROVIDER_SWITCHED, error.message)
        assertEquals(1, server.requestCount)
    }

    private suspend fun configure(provider: LlmProviderType) = configRepository.save(LlmProviderConfig.of(provider, server.url("/v1").toString(), "sk-test", "some-model"))

    private companion object {
        const val PROVIDER_SWITCHED =
            "This conversation has a reply from a different AI provider. Start a new conversation after changing the provider in Settings → AI."
    }
}
