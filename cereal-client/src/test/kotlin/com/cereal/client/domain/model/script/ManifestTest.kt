package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidManifestException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ManifestTest {
    private fun manifest(
        packageName: String = "com.example.script",
        name: String = "Example",
        versionCode: Long = 1,
    ) = Manifest(
        packageName = packageName,
        name = name,
        versionCode = versionCode,
    )

    @Test
    fun `should create valid manifest`() {
        assertNotNull(manifest())
    }

    @Test
    fun `should fail when packageName is blank`() {
        val exception = assertThrows<InvalidManifestException> { manifest(packageName = " ") }
        assertEquals("PackageName cannot be blank", exception.message)
    }

    @Test
    fun `should fail when name is blank`() {
        val exception = assertThrows<InvalidManifestException> { manifest(name = "") }
        assertEquals("Name cannot be blank", exception.message)
    }

    @Test
    fun `should fail when versionCode is negative`() {
        val exception = assertThrows<InvalidManifestException> { manifest(versionCode = -1) }
        assertEquals("VersionCode cannot be negative", exception.message)
    }
}
