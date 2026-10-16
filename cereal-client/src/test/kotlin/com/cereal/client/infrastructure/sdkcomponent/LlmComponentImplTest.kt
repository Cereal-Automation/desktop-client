package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.llm.CompleteLlmRequestInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmMessage
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.sdk.component.llm.CerealPlatformApi
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.llm.LlmRequest
import com.cereal.sdk.component.llm.LlmTurn
import com.cereal.sdk.component.llm.UserPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

@OptIn(CerealPlatformApi::class)
class LlmComponentImplTest {
    private val configRepository = InMemoryLlmProviderConfigRepository()
    private val llmProvider = InMemoryLlmProvider()
    private val component = LlmComponentImpl(CompleteLlmRequestInteractor(configRepository, llmProvider))

    private val request = LlmRequest(turns = listOf(LlmTurn.User(listOf(UserPart.Text("Hi")))))

    private suspend fun configureAnthropic() = configRepository.save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-test", "claude-opus-5-5"))

    @Test
    fun `complete without a provider set up throws NotConfigured and calls no provider`() =
        runTest {
            val error = assertThrows<LlmException.NotConfigured> { component.complete(request) }

            assertEquals("No AI provider is set up. Choose one in Settings → AI.", error.message)
            assertTrue(llmProvider.requests.isEmpty())
        }

    @Test
    fun `config is read per call so a provider saved between calls is used by the next call`() =
        runTest {
            assertThrows<LlmException.NotConfigured> { component.complete(request) }

            configureAnthropic()
            val response = component.complete(request)

            assertEquals("This is a sandbox reply.", response.text)
            assertEquals("claude-opus-5-5", response.model)
            assertEquals(
                "claude-opus-5-5",
                llmProvider.requests
                    .single()
                    .first.model,
            )
        }

    @Test
    fun `cancellation passes through complete untouched`() =
        runTest {
            configureAnthropic()
            val cancellation = CancellationException("task stopped")
            llmProvider.responder = { _, _ -> throw cancellation }

            val thrown = assertThrows<CancellationException> { component.complete(request) }

            assertSame(cancellation, thrown)
        }

    @Test
    fun `a provider failure reaches the script as the matching LlmException`() =
        runTest {
            configureAnthropic()
            llmProvider.responder = { _, _ -> throw LlmCallException.RateLimited(retryAfter = 30.seconds) }

            val error = assertThrows<LlmException.RateLimited> { component.complete(request) }

            assertEquals(30.seconds, error.retryAfter)
            assertEquals("The AI provider is rate limiting requests. Try again later.", error.message)
        }

    @Test
    fun `the assistant turn round-trips its provider payload to the next call`() =
        runTest {
            configureAnthropic()
            llmProvider.responder = { config, _ -> InMemoryLlmProvider.sandboxReply(config.model).copy(providerPayload = "thinking-blob") }

            val first = component.complete(request)
            component.complete(request.copy(turns = request.turns + first.assistantTurn))

            val sentBack =
                llmProvider.requests
                    .last()
                    .second.messages
                    .last() as LlmMessage.Assistant
            assertEquals("thinking-blob", sentBack.providerPayload)
            assertEquals("This is a sandbox reply.", sentBack.text)
        }
}
