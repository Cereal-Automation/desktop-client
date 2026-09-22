package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthorEmbedTest {
    @Test
    fun `should create valid AuthorEmbed with name`() {
        val author = AuthorEmbed(name = "Author Name")
        assertNotNull(author)
    }

    @Test
    fun `should fail when name is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(name = "   ")
            }
        assertEquals("Author name cannot be blank", exception.message)
    }

    @Test
    fun `should fail when name exceeds 256 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(name = "a".repeat(257))
            }
        assertEquals("Author name cannot exceed 256 characters", exception.message)
    }

    @Test
    fun `should accept name with exactly 256 characters`() {
        val author = AuthorEmbed(name = "a".repeat(256))
        assertNotNull(author)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(url = "   ")
            }
        assertEquals("Author URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(url = "ftp://example.com")
            }
        assertEquals("Author URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP URL`() {
        val author = AuthorEmbed(url = "http://example.com")
        assertNotNull(author)
    }

    @Test
    fun `should accept valid HTTPS URL`() {
        val author = AuthorEmbed(url = "https://example.com")
        assertNotNull(author)
    }

    @Test
    fun `should fail when icon URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(iconUrl = "   ")
            }
        assertEquals("Author icon URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when icon URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                AuthorEmbed(iconUrl = "ftp://example.com/icon.png")
            }
        assertEquals("Author icon URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP icon URL`() {
        val author = AuthorEmbed(iconUrl = "http://example.com/icon.png")
        assertNotNull(author)
    }

    @Test
    fun `should accept valid HTTPS icon URL`() {
        val author = AuthorEmbed(iconUrl = "https://example.com/icon.png")
        assertNotNull(author)
    }

    @Test
    fun `should create author with all fields`() {
        val author =
            AuthorEmbed(
                name = "John Doe",
                url = "https://example.com/john",
                iconUrl = "https://example.com/john.png",
                proxyIconUrl = "https://proxy.example.com/john.png",
            )
        assertNotNull(author)
    }
}
