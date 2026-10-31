package com.cereal.client.application.agent

import com.cereal.client.domain.model.script.entryIds
import fixtures.aScriptPackageWithVariant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadyMadeEntriesTest {
    @Test
    fun `a regular package lists its variants and can be uninstalled`() {
        val pkg = aScriptPackageWithVariant("com.example", variantId = "checkout")

        assertEquals(listOf("checkout"), pkg.entryIds)
        assertTrue(pkg.canUninstall)
    }

    @Test
    fun `the agent package lists only its variants and cannot be uninstalled`() {
        val pkg = aScriptPackageWithVariant(AI_BROWSER_AGENT_PACKAGE, variantId = "checkout")

        assertEquals(listOf("checkout"), pkg.entryIds)
        assertFalse(pkg.canUninstall)
    }
}
