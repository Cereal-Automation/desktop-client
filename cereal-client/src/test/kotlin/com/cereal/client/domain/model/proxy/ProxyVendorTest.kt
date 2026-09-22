package com.cereal.client.domain.model.proxy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyVendorTest {
    @Test
    fun `MarsProxies is the only connectable vendor in this slice`() {
        assertTrue(ProxyVendor.MARSPROXIES.available)

        val comingSoon = ProxyVendor.entries.filterNot { it.available }
        assertEquals(
            listOf(
                ProxyVendor.BRIGHT_DATA,
                ProxyVendor.OXYLABS,
                ProxyVendor.IPROYAL,
                ProxyVendor.SMARTPROXY,
            ),
            comingSoon,
        )
    }

    @Test
    fun `every non-MarsProxies vendor is marked unavailable`() {
        ProxyVendor.entries
            .filter { it != ProxyVendor.MARSPROXIES }
            .forEach { assertFalse(it.available, "${it.name} should not be available yet") }
    }

    @Test
    fun `valueOf round-trips the stored enum name`() {
        ProxyVendor.entries.forEach { vendor ->
            assertEquals(vendor, ProxyVendor.valueOf(vendor.name))
        }
    }
}
