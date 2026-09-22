package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiscordNotificationDataTest {
    private val validWebhook = "https://discord.com/api/webhooks/123/abc"

    @Test
    fun `should create valid DiscordNotificationData with content only`() {
        val data =
            DiscordNotificationData(
                content = "Hello Discord!",
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
        assertEquals("Hello Discord!", data.content)
    }

    @Test
    fun `should create valid DiscordNotificationData with embeds only`() {
        val embed = DiscordEmbed(title = "Test Title")
        val data =
            DiscordNotificationData(
                embeds = listOf(embed),
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
        assertEquals(1, data.embeds.size)
    }

    @Test
    fun `should fail when neither content nor embeds provided`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(webhookUrl = validWebhook)
            }
        assertEquals("Discord notification must have either content or at least one embed", exception.message)
    }

    @Test
    fun `should fail when embeds list is empty`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(embeds = emptyList(), webhookUrl = validWebhook)
            }
        assertEquals("Discord notification must have either content or at least one embed", exception.message)
    }

    @Test
    fun `should fail when username is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    username = "   ",
                    webhookUrl = validWebhook,
                )
            }
        assertEquals("Username cannot be blank", exception.message)
    }

    @Test
    fun `should fail when username exceeds 80 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    username = "a".repeat(81),
                    webhookUrl = validWebhook,
                )
            }
        assertEquals("Username cannot exceed 80 characters", exception.message)
    }

    @Test
    fun `should accept username with exactly 80 characters`() {
        val data =
            DiscordNotificationData(
                content = "Test",
                username = "a".repeat(80),
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
    }

    @Test
    fun `should fail when content exceeds 2000 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "a".repeat(2001),
                    webhookUrl = validWebhook,
                )
            }
        assertEquals("Content cannot exceed 2000 characters", exception.message)
    }

    @Test
    fun `should accept content with exactly 2000 characters`() {
        val data =
            DiscordNotificationData(
                content = "a".repeat(2000),
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
    }

    @Test
    fun `should fail when avatar URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    avatarUrl = "   ",
                    webhookUrl = validWebhook,
                )
            }
        assertEquals("Avatar URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when avatar URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    avatarUrl = "ftp://example.com/avatar.png",
                    webhookUrl = validWebhook,
                )
            }
        assertEquals("Avatar URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTP avatar URL`() {
        val data =
            DiscordNotificationData(
                content = "Test",
                avatarUrl = "http://example.com/avatar.png",
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
    }

    @Test
    fun `should accept valid HTTPS avatar URL`() {
        val data =
            DiscordNotificationData(
                content = "Test",
                avatarUrl = "https://example.com/avatar.png",
                webhookUrl = validWebhook,
            )
        assertNotNull(data)
    }

    @Test
    fun `should fail when embeds exceed 10`() {
        val embeds = List(11) { DiscordEmbed(title = "Embed $it") }
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(embeds = embeds, webhookUrl = validWebhook)
            }
        assertEquals("Cannot have more than 10 embeds per Discord notification", exception.message)
    }

    @Test
    fun `should accept exactly 10 embeds`() {
        val embeds = List(10) { DiscordEmbed(title = "Embed $it") }
        val data = DiscordNotificationData(embeds = embeds, webhookUrl = validWebhook)
        assertNotNull(data)
        assertEquals(10, data.embeds.size)
    }

    @Test
    fun `should fail when webhook URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    webhookUrl = "   ",
                )
            }
        assertEquals("Webhook URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when webhook URL is not a Discord webhook`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                DiscordNotificationData(
                    content = "Test",
                    webhookUrl = "https://example.com/webhook",
                )
            }
        assertEquals("Webhook URL must be a valid Discord webhook URL", exception.message)
    }

    @Test
    fun `should accept valid discord com webhook URL`() {
        val data =
            DiscordNotificationData(
                content = "Test",
                webhookUrl = "https://discord.com/api/webhooks/123456/abcdef",
            )
        assertNotNull(data)
    }

    @Test
    fun `should accept valid discordapp com webhook URL`() {
        val data =
            DiscordNotificationData(
                content = "Test",
                webhookUrl = "https://discordapp.com/api/webhooks/123456/abcdef",
            )
        assertNotNull(data)
    }

    @Test
    fun `should create valid notification with all optional fields`() {
        val embed = DiscordEmbed(title = "Test")
        val data =
            DiscordNotificationData(
                username = "TestBot",
                content = "Hello",
                avatarUrl = "https://example.com/avatar.png",
                tts = true,
                embeds = listOf(embed),
                webhookUrl = "https://discord.com/api/webhooks/123/abc",
            )
        assertNotNull(data)
        assertEquals("TestBot", data.username)
        assertEquals("Hello", data.content)
        assertEquals(true, data.tts)
    }
}
