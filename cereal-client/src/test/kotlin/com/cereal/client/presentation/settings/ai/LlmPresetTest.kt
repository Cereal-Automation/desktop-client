package com.cereal.client.presentation.settings.ai

import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class LlmPresetTest {
    @ParameterizedTest
    @EnumSource(LlmPreset::class, names = ["GEMINI", "ANTHROPIC", "OPENAI", "OPENROUTER"])
    fun `a keyed preset round-trips through the stored config`(preset: LlmPreset) {
        val config = LlmProviderConfig.of(preset.provider, preset.baseUrl, "key-1", preset.model)

        assertEquals(preset, LlmPreset.of(config))
    }

    @ParameterizedTest
    @CsvSource(
        "GEMINI, OPENAI_COMPATIBLE, https://generativelanguage.googleapis.com/v1beta/openai, gemini-3.8-flash",
        "ANTHROPIC, ANTHROPIC, https://api.anthropic.com/v1, claude-opus-5-5",
        "OPENAI, OPENAI_COMPATIBLE, https://api.openai.com/v1, gpt-5",
        "OPENROUTER, OPENAI_COMPATIBLE, https://openrouter.ai/api/v1, anthropic/claude-opus-5.5",
        "LOCAL, OPENAI_COMPATIBLE, http://localhost:11434/v1, ''",
        "OTHER, OPENAI_COMPATIBLE, '', ''",
    )
    fun `each preset prefills its provider type, base URL and model`(
        preset: LlmPreset,
        provider: LlmProviderType,
        baseUrl: String,
        model: String,
    ) {
        assertEquals(provider, preset.provider)
        assertEquals(baseUrl, preset.baseUrl)
        assertEquals(model, preset.model)
    }

    @ParameterizedTest
    @CsvSource("http://localhost:11434/v1", "http://localhost:1234/v1", "http://127.0.0.1:8080/v1")
    fun `a local server round-trips to Local`(baseUrl: String) {
        assertEquals(LlmPreset.LOCAL, LlmPreset.of(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, baseUrl, "", "llama3")))
    }

    @ParameterizedTest
    @CsvSource("https://my-proxy.example.com/v1", "https://api.together.xyz/v1")
    fun `an unknown OpenAI-compatible host round-trips to Other`(baseUrl: String) {
        assertEquals(LlmPreset.OTHER, LlmPreset.of(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, baseUrl, "k", "m")))
    }

    @Test
    fun `Anthropic behind a proxy opens on Anthropic, the only preset of that type`() {
        val config = LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://proxy.example.com/v1", "k", "claude-opus-5-5")

        assertEquals(LlmPreset.ANTHROPIC, LlmPreset.of(config))
    }

    @Test
    fun `gemini note covers the free tier`() =
        runTest {
            assertEquals(
                "The free tier doesn't bill you, but it's rate-limited and Google may use your prompts to improve its products. " +
                    "On a paid key, set a budget alert in [Google Cloud Billing ↗](https://console.cloud.google.com/billing).",
                getString(LlmPreset.GEMINI.spendLimitsHelp()!!),
            )
        }

    @Test
    fun `openrouter note covers free models`() =
        runTest {
            assertEquals(
                "OpenRouter spends your prepaid credits. Free models cost nothing but have daily request limits, " +
                    "and some of their providers may log your prompts. " +
                    "Set a credit limit on your key in [OpenRouter keys ↗](https://openrouter.ai/settings/keys).",
                getString(LlmPreset.OPENROUTER.spendLimitsHelp()!!),
            )
        }
}
