package com.cereal.client.domain.model.llm

import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.script.configuration.Secret
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LlmProviderConfigTest {
    @Test
    fun `Anthropic defaults to its base URL and claude-opus-5-5`() {
        assertEquals("https://api.anthropic.com/v1", LlmProviderType.ANTHROPIC.defaultBaseUrl)
        assertEquals("claude-opus-5-5", LlmProviderType.ANTHROPIC.defaultModel)
    }

    @Test
    fun `OpenAI-compatible defaults to the OpenAI base URL and no model`() {
        assertEquals("https://api.openai.com/v1", LlmProviderType.OPENAI_COMPATIBLE.defaultBaseUrl)
        assertNull(LlmProviderType.OPENAI_COMPATIBLE.defaultModel)
    }

    @Test
    fun `of builds a config from form input`() {
        val config =
            LlmProviderConfig.of(
                provider = LlmProviderType.ANTHROPIC,
                baseUrl = " https://api.anthropic.com/v1/ ",
                apiKey = "sk-ant-1",
                model = " claude-opus-5-5 ",
            )

        assertEquals(LlmProviderType.ANTHROPIC, config.provider)
        assertEquals("https://api.anthropic.com/v1", config.baseUrl.value)
        assertEquals(Secret("sk-ant-1"), config.apiKey)
        assertEquals("claude-opus-5-5", config.model)
    }

    @Test
    fun `of rejects a non-http base URL`() {
        val error =
            assertThrows<InvalidLlmProviderConfigException> {
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "ftp://localhost:11434", "", "llama3")
            }
        assertEquals(LlmProviderConfig.Field.BASE_URL, error.field)
    }

    @Test
    fun `of rejects a base URL without a host`() {
        val error =
            assertThrows<InvalidLlmProviderConfigException> {
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http:localhost", "", "llama3")
            }
        assertEquals(LlmProviderConfig.Field.BASE_URL, error.field)
    }

    @Test
    fun `of rejects a blank model`() {
        val error =
            assertThrows<InvalidLlmProviderConfigException> {
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "  ")
            }
        assertEquals(LlmProviderConfig.Field.MODEL, error.field)
    }

    @Test
    fun `Anthropic requires an API key`() {
        val error =
            assertThrows<InvalidLlmProviderConfigException> {
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", " ", "claude-opus-5-5")
            }
        assertEquals(LlmProviderConfig.Field.API_KEY, error.field)
    }

    @Test
    fun `OpenAI-compatible accepts a missing API key`() {
        val config = LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "llama3")

        assertNull(config.apiKey)
    }

    @Test
    fun `toString never shows the API key`() {
        val config = LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-secret", "claude-opus-5-5")

        assert("sk-ant-secret" !in config.toString())
    }
}
