package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable version of DiscordMessage for JSON serialization.
 * This mirrors the SDK DiscordMessage but is specifically designed for kotlinx serialization.
 */
@Serializable
data class SerializableDiscordMessage(
    val username: String? = null,
    val content: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val tts: Boolean? = null,
    val embeds: List<SerializableDiscordEmbed>? = null,
    @SerialName("webhook_url") val webhookUrl: String? = null,
)
