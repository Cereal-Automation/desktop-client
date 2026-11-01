package com.cereal.client.presentation.settings.ai

import com.cereal.client.domain.model.llm.LlmModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class LlmModelUiMapperTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `maps name, id, the preset's free badge and the date in the given zone`() {
        val model = LlmModel("meta/llama", displayName = "Llama", created = Instant.parse("2026-03-04T23:30:00Z"), pricedFree = true)

        assertEquals(LlmModelUiModel("meta/llama", "Llama", free = true, date = "2026-03-04"), model.toUiModel(LlmPreset.OPENROUTER, utc))
        assertEquals("2026-03-05", model.toUiModel(LlmPreset.OPENROUTER, ZoneId.of("Europe/Amsterdam")).date)
    }

    @Test
    fun `a first-party preset never shows free and an undated model has no date`() {
        assertEquals(LlmModelUiModel("gpt-5", null, free = false, date = null), LlmModel("gpt-5", pricedFree = true).toUiModel(LlmPreset.OPENAI, utc))
    }
}
