package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

class DiscordEmbedTest {
    @Test
    fun `should create valid DiscordEmbed with minimal data`() {
        val embed = DiscordEmbed(title = "Test Title")
        assertNotNull(embed)
    }

    @Test
    fun `should fail when title is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(title = "   ")
            }
        kotlin.test.assertEquals("Embed title cannot be blank", exception.message)
    }

    @Test
    fun `should fail when title exceeds 256 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(title = "a".repeat(257))
            }
        kotlin.test.assertEquals("Embed title cannot exceed 256 characters", exception.message)
    }

    @Test
    fun `should accept title with exactly 256 characters`() {
        val embed = DiscordEmbed(title = "a".repeat(256))
        assertNotNull(embed)
    }

    @Test
    fun `should fail when description exceeds 4096 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(description = "a".repeat(4097))
            }
        kotlin.test.assertEquals("Embed description cannot exceed 4096 characters", exception.message)
    }

    @Test
    fun `should accept description with exactly 4096 characters`() {
        val embed = DiscordEmbed(description = "a".repeat(4096))
        assertNotNull(embed)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(url = "   ")
            }
        kotlin.test.assertEquals("Embed URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(url = "ftp://example.com")
            }
        kotlin.test.assertEquals("Embed URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTPS URL`() {
        val embed = DiscordEmbed(url = "https://example.com")
        assertNotNull(embed)
    }

    @Test
    fun `should fail when timestamp is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(timestamp = "   ")
            }
        kotlin.test.assertEquals("Timestamp cannot be blank", exception.message)
    }

    @Test
    fun `should fail when timestamp is not ISO 8601 format`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(timestamp = "2024-11-15 10:30:00")
            }
        kotlin.test.assertEquals("Timestamp must be in ISO 8601 format", exception.message)
    }

    @Test
    fun `should accept valid ISO 8601 timestamp`() {
        val embed = DiscordEmbed(timestamp = "2024-11-15T10:30:00Z")
        assertNotNull(embed)
    }

    @Test
    fun `should accept ISO 8601 timestamp with timezone offset`() {
        val embed = DiscordEmbed(timestamp = "2024-11-15T10:30:00+02:00")
        assertNotNull(embed)
    }

    @Test
    fun `should fail when color is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(color = "   ")
            }
        kotlin.test.assertEquals("Color cannot be blank", exception.message)
    }

    @Test
    fun `should accept valid color`() {
        val embed = DiscordEmbed(color = "#FF0000")
        assertNotNull(embed)
    }

    @Test
    fun `should fail when fields exceed 25`() {
        val fields = List(26) { FieldEmbed(name = "Field $it", value = "Value $it") }
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(fields = fields)
            }
        kotlin.test.assertEquals("Embed cannot have more than 25 fields", exception.message)
    }

    @Test
    fun `should accept exactly 25 fields`() {
        val fields = List(25) { FieldEmbed(name = "Field $it", value = "Value $it") }
        val embed = DiscordEmbed(fields = fields)
        assertNotNull(embed)
    }

    @Test
    fun `should fail when total embed size exceeds 6000 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordEmbed(
                    title = "a".repeat(256),
                    description = "a".repeat(4096),
                    footer = FooterEmbed(text = "a".repeat(2000)),
                    author = AuthorEmbed(name = "a".repeat(256)),
                )
            }
        kotlin.test.assertTrue(exception.message!!.contains("Total embed size cannot exceed 6000 characters"))
    }

    @Test
    fun `should accept valid total embed size`() {
        val embed =
            DiscordEmbed(
                title = "a".repeat(100),
                description = "a".repeat(2000),
                footer = FooterEmbed(text = "Footer"),
                author = AuthorEmbed(name = "Author"),
                fields =
                    listOf(
                        FieldEmbed(name = "Field 1", value = "Value 1"),
                    ),
            )
        assertNotNull(embed)
    }

    @Test
    fun `should create embed with all optional fields`() {
        val embed =
            DiscordEmbed(
                title = "Title",
                type = "rich",
                description = "Description",
                url = "https://example.com",
                timestamp = "2024-11-15T10:30:00Z",
                color = "#FF0000",
                footer = FooterEmbed(text = "Footer"),
                image = ImageEmbed(url = "https://example.com/image.png"),
                thumbnail = ThumbnailEmbed(url = "https://example.com/thumb.png"),
                video = VideoEmbed(url = "https://example.com/video.mp4"),
                provider = ProviderEmbed(name = "Provider"),
                author = AuthorEmbed(name = "Author"),
                fields = listOf(FieldEmbed(name = "Field", value = "Value")),
            )
        assertNotNull(embed)
    }
}
