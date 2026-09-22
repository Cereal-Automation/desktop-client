package com.cereal.client.infrastructure.data.notification.discord.mapper

import com.cereal.client.infrastructure.data.notification.discord.mapper.DiscordModelMapper
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableDiscordMessage
import com.cereal.sdk.component.notification.discord.model.DiscordEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import com.cereal.sdk.component.notification.discord.model.embed.AuthorEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FieldEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FooterEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ImageEmbed
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscordSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test DiscordMessage serialization with basic fields`() {
        // Given
        val sdkMessage =
            DiscordMessage(
                username = "TestBot",
                content = "Hello, World!",
                avatarUrl = "https://example.com/avatar.png",
                tts = true,
                webhookUrl = "https://discord.com/api/webhooks/123/abc",
            )

        // When
        val serializableMessage = DiscordModelMapper.toSerializable(sdkMessage)
        val jsonString = json.encodeToString(serializableMessage)

        // Then
        assertTrue(jsonString.contains("\"username\":\"TestBot\""))
        assertTrue(jsonString.contains("\"content\":\"Hello, World!\""))
        assertTrue(jsonString.contains("\"avatar_url\":\"https://example.com/avatar.png\""))
        assertTrue(jsonString.contains("\"tts\":true"))
        assertTrue(jsonString.contains("\"webhook_url\":\"https://discord.com/api/webhooks/123/abc\""))
    }

    @Test
    fun `test DiscordMessage serialization with embeds`() {
        // Given
        val embed =
            DiscordEmbed(
                title = "Test Embed",
                type = null,
                description = "This is a test embed",
                url = "https://example.com",
                timestamp = "2023-01-01T00:00:00.000Z",
                color = "16711680", // Red color
                footer =
                    FooterEmbed(
                        text = "Footer text",
                        iconUrl = "https://example.com/footer.png",
                        proxyIconUrl = "https://proxy.example.com/footer.png",
                    ),
                image =
                    ImageEmbed(
                        url = "https://example.com/image.png",
                        proxyUrl = "https://proxy.example.com/image.png",
                        height = 100,
                        width = 200,
                    ),
                thumbnail = null,
                video = null,
                provider = null,
                author =
                    AuthorEmbed(
                        name = "Author Name",
                        url = "https://example.com/author",
                        iconUrl = "https://example.com/author.png",
                        proxyIconUrl = "https://proxy.example.com/author.png",
                    ),
                fields =
                    listOf(
                        FieldEmbed(
                            name = "Field 1",
                            value = "Value 1",
                            inline = true,
                        ),
                        FieldEmbed(
                            name = "Field 2",
                            value = "Value 2",
                            inline = false,
                        ),
                    ),
            )

        val sdkMessage =
            DiscordMessage(
                content = "Message with embed",
                embeds = listOf(embed),
            )

        // When
        val serializableMessage = DiscordModelMapper.toSerializable(sdkMessage)
        val jsonString = json.encodeToString(serializableMessage)

        // Then
        assertTrue(jsonString.contains("\"content\":\"Message with embed\""))
        assertTrue(jsonString.contains("\"title\":\"Test Embed\""))
        assertTrue(jsonString.contains("\"description\":\"This is a test embed\""))
        assertTrue(jsonString.contains("\"color\":\"16711680\""))
        assertTrue(jsonString.contains("\"icon_url\":\"https://example.com/footer.png\""))
        assertTrue(jsonString.contains("\"proxy_icon_url\":\"https://proxy.example.com/footer.png\""))
        assertTrue(jsonString.contains("\"proxy_url\":\"https://proxy.example.com/image.png\""))
        assertTrue(jsonString.contains("\"inline\":true"))
        assertTrue(jsonString.contains("\"inline\":false"))
    }

    @Test
    fun `test DiscordMessage deserialization`() {
        // Given
        val jsonString =
            """
            {
                "username": "TestBot",
                "content": "Hello, World!",
                "avatar_url": "https://example.com/avatar.png",
                "tts": true,
                "webhook_url": "https://discord.com/api/webhooks/123/abc"
            }
            """.trimIndent()

        // When
        val serializableMessage = json.decodeFromString<SerializableDiscordMessage>(jsonString)

        // Then
        assertEquals("TestBot", serializableMessage.username)
        assertEquals("Hello, World!", serializableMessage.content)
        assertEquals("https://example.com/avatar.png", serializableMessage.avatarUrl)
        assertEquals(true, serializableMessage.tts)
        assertEquals("https://discord.com/api/webhooks/123/abc", serializableMessage.webhookUrl)
    }

    @Test
    fun `test DiscordMessage with null values serialization`() {
        // Given
        val sdkMessage =
            DiscordMessage(
                username = null,
                content = "Only content",
                avatarUrl = null,
                tts = null,
                embeds = null,
                webhookUrl = null,
            )

        // When
        val serializableMessage = DiscordModelMapper.toSerializable(sdkMessage)
        val jsonString = json.encodeToString(serializableMessage)

        // Then
        assertTrue(jsonString.contains("\"content\":\"Only content\""))
        // Null values should not be included in the JSON
        assertTrue(!jsonString.contains("\"username\"") || jsonString.contains("\"username\":null"))
        assertTrue(!jsonString.contains("\"avatar_url\"") || jsonString.contains("\"avatar_url\":null"))
        assertTrue(!jsonString.contains("\"tts\"") || jsonString.contains("\"tts\":null"))
        assertTrue(!jsonString.contains("\"embeds\"") || jsonString.contains("\"embeds\":null"))
        assertTrue(!jsonString.contains("\"webhook_url\"") || jsonString.contains("\"webhook_url\":null"))
    }
}
