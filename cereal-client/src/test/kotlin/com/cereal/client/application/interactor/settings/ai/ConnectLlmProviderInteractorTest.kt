package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConnectLlmProviderInteractorTest {
    private val repository = InMemoryLlmProviderConfigRepository()
    private val llmProvider = InMemoryLlmProvider()
    private val interactor = ConnectLlmProviderInteractor(llmProvider, SaveLlmProviderConfigInteractor(repository))

    private val gemini =
        ConnectLlmProviderInteractor.Params(
            provider = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            apiKey = "key",
            model = "gemini-2.5-flash",
        )

    @Test
    fun `a passing connection test saves the config`() =
        runTest {
            val saved = interactor.run(gemini)

            assertEquals("gemini-2.5-flash", repository.get()?.model)
            assertEquals(repository.get(), saved)
        }

    @Test
    fun `a failing connection test saves nothing`() =
        runTest {
            llmProvider.listModelsError = LlmCallException.Authentication()

            assertThrows<LlmCallException.Authentication> { interactor.run(gemini) }
            assertNull(repository.get())
        }

    @Test
    fun `invalid input saves nothing`() =
        runTest {
            assertThrows<InvalidLlmProviderConfigException> { interactor.run(gemini.copy(model = " ")) }
            assertNull(repository.get())
        }
}
