package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageEmbedTest {
    @Test
    fun `should create valid ImageEmbed with URL`() {
        val image = ImageEmbed(url = "https://example.com/image.png")
        assertNotNull(image)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "   ")
            }
        assertEquals("Image URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "ftp://example.com/image.png")
            }
        assertEquals("Image URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP URL`() {
        val image = ImageEmbed(url = "http://example.com/image.png")
        assertNotNull(image)
    }

    @Test
    fun `should fail when height is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "https://example.com/image.png", height = 0)
            }
        assertEquals("Image height must be positive", exception.message)
    }

    @Test
    fun `should fail when height is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "https://example.com/image.png", height = -100)
            }
        assertEquals("Image height must be positive", exception.message)
    }

    @Test
    fun `should fail when width is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "https://example.com/image.png", width = 0)
            }
        assertEquals("Image width must be positive", exception.message)
    }

    @Test
    fun `should fail when width is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                ImageEmbed(url = "https://example.com/image.png", width = -200)
            }
        assertEquals("Image width must be positive", exception.message)
    }

    @Test
    fun `should accept valid dimensions`() {
        val image =
            ImageEmbed(
                url = "https://example.com/image.png",
                height = 1080,
                width = 1920,
            )
        assertNotNull(image)
    }

    @Test
    fun `should create image with all fields`() {
        val image =
            ImageEmbed(
                url = "https://example.com/image.png",
                proxyUrl = "https://proxy.example.com/image.png",
                height = 1080,
                width = 1920,
            )
        assertNotNull(image)
    }
}
