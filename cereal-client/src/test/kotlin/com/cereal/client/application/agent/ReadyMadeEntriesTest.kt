package com.cereal.client.application.agent

import fixtures.aScriptPackageWithVariant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadyMadeEntriesTest {
    @Test
    fun `a regular package lists only its default entry and can be uninstalled`() {
        val pkg = aScriptPackageWithVariant("com.example", variantId = "checkout")

        assertEquals(listOf(null), pkg.readyMadeEntryIds)
        assertTrue(pkg.canUninstall)
    }

    @Test
    fun `the agent package lists only its variants and cannot be uninstalled`() {
        val pkg = aScriptPackageWithVariant(AI_BROWSER_AGENT_PACKAGE, variantId = "checkout")

        assertEquals(listOf("checkout"), pkg.readyMadeEntryIds)
        assertFalse(pkg.canUninstall)
    }
}
