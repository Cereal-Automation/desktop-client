package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.UnknownScriptVariantException
import fixtures.aScriptPackageInstance
import fixtures.aScriptPackageWithVariant
import fixtures.withIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ScriptPackageInstanceEntryScriptTest {
    @Test
    fun `entry script of an instance without a variant is the package's main script`() {
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example")

        assertSame(instance.definition.mainScript, instance.entryScript)
    }

    @Test
    fun `entry script of a variant instance is the variant's script`() {
        val definition = aScriptPackageWithVariant("com.example", variantId = "checkout")
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example", definition = definition, variantId = "checkout")

        assertSame(definition.variants.getValue("checkout").script, instance.entryScript)
    }

    @Test
    fun `an instance whose variant is not in the package fails instead of running the default entry`() {
        val definition = aScriptPackageWithVariant("com.example", variantId = "checkout")
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example", definition = definition, variantId = "removed")

        assertFailsWith<UnknownScriptVariantException> { instance.entryScript }
    }

    @Test
    fun `a variant instance is named by its variant script's identifier`() {
        val definition = aScriptPackageWithVariant("com.example", variantId = "checkout", variantName = "Checkout")
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example", definition = definition, variantId = "checkout")

        assertEquals("Size 42", instance.withIdentifier("Size 42").displayName)
    }

    @Test
    fun `an instance with a blank or unset identifier is named by its entry`() {
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example")

        assertNull(instance.withIdentifier(" ").identifierValue)
        assertEquals(instance.entryName, instance.withIdentifier(" ").displayName)
        assertEquals(instance.entryName, instance.withIdentifier(null).displayName)
    }
}
