package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.UnknownScriptVariantException
import fixtures.aScriptPackageInstance
import fixtures.aScriptPackageWithVariant
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
}
