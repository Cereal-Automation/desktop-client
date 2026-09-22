package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ThumbnailEmbedTest {
    @Test
    fun `should create valid ThumbnailEmbed with URL`() {
        val thumbnail = ThumbnailEmbed(url = "https://example.com/thumb.png")
        assertNotNull(thumbnail)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "   ")
            }
        assertEquals("Thumbnail URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "ftp://example.com/thumb.png")
            }
        assertEquals("Thumbnail URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTPS URL`() {
        val thumbnail = ThumbnailEmbed(url = "https://example.com/thumb.png")
        assertNotNull(thumbnail)
    }

    @Test
    fun `should fail when height is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "https://example.com/thumb.png", height = 0)
            }
        assertEquals("Thumbnail height must be positive", exception.message)
    }

    @Test
    fun `should fail when height is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "https://example.com/thumb.png", height = -50)
            }
        assertEquals("Thumbnail height must be positive", exception.message)
    }

    @Test
    fun `should fail when width is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "https://example.com/thumb.png", width = 0)
            }
        assertEquals("Thumbnail width must be positive", exception.message)
    }

    @Test
    fun `should fail when width is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ThumbnailEmbed(url = "https://example.com/thumb.png", width = -100)
            }
        assertEquals("Thumbnail width must be positive", exception.message)
    }

    @Test
    fun `should accept valid dimensions`() {
        val thumbnail =
            ThumbnailEmbed(
                url = "https://example.com/thumb.png",
                height = 200,
                width = 200,
            )
        assertNotNull(thumbnail)
    }

    @Test
    fun `should create thumbnail with all fields`() {
        val thumbnail =
            ThumbnailEmbed(
                url = "https://example.com/thumb.png",
                proxyUrl = "https://proxy.example.com/thumb.png",
                height = 200,
                width = 200,
            )
        assertNotNull(thumbnail)
    }
}
