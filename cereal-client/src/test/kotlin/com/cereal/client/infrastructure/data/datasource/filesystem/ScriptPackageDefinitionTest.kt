package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.script.ScriptFeature
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ScriptPackageDefinitionTest {
    @Test
    fun `toScriptPackage maps llm to the LLM script feature`() {
        val scriptPackage = aDefinition(features = listOf("llm")).toScriptPackage(supportUrl = null)

        assertEquals(setOf(ScriptFeature.LLM), scriptPackage.manifest.features)
    }

    @Test
    fun `toScriptPackage drops unknown feature values and keeps the known ones`() {
        val scriptPackage = aDefinition(features = listOf("teleport", "llm")).toScriptPackage(supportUrl = null)

        assertEquals(setOf(ScriptFeature.LLM), scriptPackage.manifest.features)
    }

    @Test
    fun `toScriptPackage gives no script features when none are declared`() {
        val scriptPackage = aDefinition(features = emptyList()).toScriptPackage(supportUrl = null)

        assertEquals(emptySet(), scriptPackage.manifest.features)
    }

    private fun aDefinition(features: List<String>) =
        ScriptPackageDefinition(
            source = File("/tmp/fake.jar"),
            manifest =
                ManifestDefinition(
                    packageName = "com.example.test",
                    name = "Test Script",
                    versionCode = 1L,
                    features = features,
                ),
            mainScript = mockk(relaxed = true),
            childScripts = emptyMap(),
        )
}
