package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FooterEmbedTest {
    @Test
    fun `should create valid FooterEmbed with text`() {
        val footer = FooterEmbed(text = "Footer text")
        assertNotNull(footer)
    }

    @Test
    fun `should fail when text is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FooterEmbed(text = "   ")
            }
        assertEquals("Footer text cannot be blank", exception.message)
    }

    @Test
    fun `should fail when text exceeds 2048 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FooterEmbed(text = "a".repeat(2049))
            }
        assertEquals("Footer text cannot exceed 2048 characters", exception.message)
    }

    @Test
    fun `should accept text with exactly 2048 characters`() {
        val footer = FooterEmbed(text = "a".repeat(2048))
        assertNotNull(footer)
    }

    @Test
    fun `should fail when icon URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FooterEmbed(iconUrl = "   ")
            }
        assertEquals("Footer icon URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when icon URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FooterEmbed(iconUrl = "ftp://example.com/icon.png")
            }
        assertEquals("Footer icon URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP icon URL`() {
        val footer = FooterEmbed(iconUrl = "http://example.com/icon.png")
        assertNotNull(footer)
    }

    @Test
    fun `should accept valid HTTPS icon URL`() {
        val footer = FooterEmbed(iconUrl = "https://example.com/icon.png")
        assertNotNull(footer)
    }

    @Test
    fun `should create footer with all fields`() {
        val footer =
            FooterEmbed(
                text = "Footer text",
                iconUrl = "https://example.com/icon.png",
                proxyIconUrl = "https://proxy.example.com/icon.png",
            )
        assertNotNull(footer)
    }
}
