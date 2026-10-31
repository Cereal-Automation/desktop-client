package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.UnknownScriptVariantException
import fixtures.aScriptPackage
import fixtures.aScriptPackageWithVariant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ScriptVariantTest {
    @Test
    fun `a variant with a blank name is rejected`() {
        assertFailsWith<IllegalArgumentException> { aScriptPackageWithVariant("com.example", variantName = " ") }
    }

    @Test
    fun `a package with a blank variant id is rejected`() {
        assertFailsWith<IllegalArgumentException> { aScriptPackageWithVariant("com.example", variantId = "") }
    }

    @Test
    fun `a package without variants offers only its default entry`() {
        assertEquals(listOf(null), aScriptPackage("com.example").entryIds)
    }

    @Test
    fun `a package with variants offers only its variants`() {
        assertEquals(listOf("checkout"), aScriptPackageWithVariant("com.example", variantId = "checkout").entryIds)
    }

    @Test
    fun `a package with variants has no default entry`() {
        val definition = aScriptPackageWithVariant("com.example", variantId = "checkout")

        assertFailsWith<UnknownScriptVariantException> { definition.entry(null) }
        assertFailsWith<UnknownScriptVariantException> { definition.entryName(null) }
        assertFailsWith<UnknownScriptVariantException> { definition.entryInstructions(null) }
    }

    @Test
    fun `a package needs a main script or variants`() {
        assertFailsWith<IllegalArgumentException> { aScriptPackage("com.example").copy(mainScript = null) }
    }

    @Test
    fun `a package with variants needs no main script`() {
        val definition = aScriptPackageWithVariant("com.example", variantId = "checkout").copy(mainScript = null)

        assertEquals(listOf("checkout"), definition.entryIds)
    }
}
