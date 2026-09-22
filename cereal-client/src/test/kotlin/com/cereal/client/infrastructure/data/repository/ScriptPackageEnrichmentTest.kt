package com.cereal.client.infrastructure.data.repository

import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScriptPackageEnrichmentTest {
    @Test
    fun `toScriptPackages overlays the support url matching each package name`() {
        val definitions = listOf(aDefinition("com.example.paid"), aDefinition("com.example.free"))
        val supportUrlByPackageName = mapOf("com.example.paid" to "https://example.com/support")

        val result = definitions.toScriptPackages(supportUrlByPackageName)

        assertEquals("https://example.com/support", result[0].manifest.supportUrl)
        assertNull(result[1].manifest.supportUrl, "unmatched package gets a null support url")
    }

    @Test
    fun `toScriptPackagesByPackageName keys the overlaid packages by package name`() {
        val definitions = listOf(aDefinition("com.example.paid"), aDefinition("com.example.free"))
        val supportUrlByPackageName = mapOf("com.example.paid" to "https://example.com/support")

        val result = definitions.toScriptPackagesByPackageName(supportUrlByPackageName)

        assertEquals(setOf("com.example.paid", "com.example.free"), result.keys)
        assertEquals("https://example.com/support", result.getValue("com.example.paid").manifest.supportUrl)
        assertNull(result.getValue("com.example.free").manifest.supportUrl)
    }

    private fun aDefinition(packageName: String) =
        ScriptPackageDefinition(
            source = File("/tmp/fake.jar"),
            manifest =
                ManifestDefinition(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(relaxed = true),
            childScripts = emptyMap(),
        )
}
