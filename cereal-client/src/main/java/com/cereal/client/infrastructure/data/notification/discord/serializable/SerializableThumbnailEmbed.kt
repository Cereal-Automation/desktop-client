package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable version of ThumbnailEmbed for JSON serialization.
 */
@Serializable
data class SerializableThumbnailEmbed(
    val url: String? = null,
    @SerialName("proxy_url") val proxyUrl: String? = null,
    val height: Int? = null,
    val width: Int? = null,
)
