package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProviderEmbedTest {
    @Test
    fun `should create valid ProviderEmbed with name`() {
        val provider = ProviderEmbed(name = "Provider Name")
        assertNotNull(provider)
    }

    @Test
    fun `should fail when name is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ProviderEmbed(name = "   ")
            }
        assertEquals("Provider name cannot be blank", exception.message)
    }

    @Test
    fun `should accept valid name`() {
        val provider = ProviderEmbed(name = "YouTube")
        assertNotNull(provider)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ProviderEmbed(url = "   ")
            }
        assertEquals("Provider URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ProviderEmbed(url = "ftp://example.com")
            }
        assertEquals("Provider URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP URL`() {
        val provider = ProviderEmbed(url = "http://example.com")
        assertNotNull(provider)
    }

    @Test
    fun `should accept valid HTTPS URL`() {
        val provider = ProviderEmbed(url = "https://example.com")
        assertNotNull(provider)
    }

    @Test
    fun `should create provider with both name and URL`() {
        val provider =
            ProviderEmbed(
                name = "YouTube",
                url = "https://youtube.com",
            )
        assertNotNull(provider)
    }
}
