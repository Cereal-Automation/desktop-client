package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.Serializable

/**
 * Serializable version of DiscordEmbed for JSON serialization.
 * This mirrors the SDK DiscordEmbed but is specifically designed for kotlinx serialization.
 */
@Serializable
data class SerializableDiscordEmbed(
    val title: String?,
    val type: String?,
    val description: String?,
    val url: String?,
    val timestamp: String?,
    val color: String?,
    val footer: SerializableFooterEmbed?,
    val image: SerializableImageEmbed?,
    val thumbnail: SerializableThumbnailEmbed?,
    val video: SerializableVideoEmbed?,
    val provider: SerializableProviderEmbed?,
    val author: SerializableAuthorEmbed?,
    val fields: List<SerializableFieldEmbed>?,
)
