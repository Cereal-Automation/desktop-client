package com.cereal.client.application.interactor.settings.ai

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HasLlmProviderConfigInteractorTest {
    @Test
    fun `run returns false when no provider config is saved`() =
        runTest {
            val interactor = HasLlmProviderConfigInteractor(InMemoryLlmProviderConfigRepository())

            assertFalse(interactor.run(Interactor.None()))
        }

    @Test
    fun `run returns true when a provider config is saved`() =
        runTest {
            val config = LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "llama3")
            val interactor = HasLlmProviderConfigInteractor(InMemoryLlmProviderConfigRepository(config))

            assertTrue(interactor.run(Interactor.None()))
        }
}
