package com.cereal.client.domain.model.script

import fixtures.aScriptPackageWithVariant
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
}
