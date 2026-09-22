package com.cereal.client.presentation.proxy.provider

import com.cereal.client.domain.model.proxy.ProxyVendor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyVendorDisplayTest {
    @Test
    fun `every vendor resolves complete, non-blank display metadata`() {
        ProxyVendor.entries.forEach { vendor ->
            val display = vendor.display
            assertTrue(display.displayName.isNotBlank(), "${vendor.name} displayName blank")
            assertTrue(display.brandMark.isNotBlank(), "${vendor.name} brandMark blank")
            assertTrue(display.tagline.isNotBlank(), "${vendor.name} tagline blank")
            assertTrue(
                display.brandColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$")),
                "${vendor.name} brandColorHex must be a 6-digit hex, was ${display.brandColorHex}",
            )
        }
    }

    @Test
    fun `MarsProxies maps to its branded name and colour`() {
        val display = ProxyVendor.MARSPROXIES.display
        assertEquals("MarsProxies", display.displayName)
        assertEquals("M", display.brandMark)
        assertEquals("#E54848", display.brandColorHex)
    }
}
