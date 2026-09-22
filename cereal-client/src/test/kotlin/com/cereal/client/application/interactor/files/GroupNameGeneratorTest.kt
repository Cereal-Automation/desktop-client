package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.script.Manifest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupNameGeneratorTest {
    private fun manifest(name: String) =
        Manifest(
            packageName = "com.example.script",
            name = name,
            versionCode = 1L,
        )

    @Test
    fun `should include the manifest name in the generated group name`() {
        val name = createGroupName(manifest("My Script"))

        assertTrue(name.startsWith("Imported for My Script at "), "Unexpected name: $name")
    }

    @Test
    fun `should append a timestamp in the expected format`() {
        val name = createGroupName(manifest("Script"))

        val timestamp = name.removePrefix("Imported for Script at ")
        assertTrue(
            timestamp.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")),
            "Unexpected timestamp: $timestamp",
        )
    }

    @Test
    fun `should reflect a different manifest name`() {
        val name = createGroupName(manifest("Another Script"))

        assertTrue(name.startsWith("Imported for Another Script at "), "Unexpected name: $name")
    }
}
