package com.cereal.client.domain.model.script

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScriptFeatureTest {
    @Test
    fun `fromManifestValue maps llm to LLM`() {
        assertEquals(ScriptFeature.LLM, ScriptFeature.fromManifestValue("llm"))
    }

    @Test
    fun `fromManifestValue returns null for an unknown value`() {
        assertNull(ScriptFeature.fromManifestValue("teleport"))
    }
}
