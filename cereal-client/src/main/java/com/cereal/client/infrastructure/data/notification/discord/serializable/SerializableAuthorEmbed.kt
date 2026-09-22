package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable version of AuthorEmbed for JSON serialization.
 */
@Serializable
data class SerializableAuthorEmbed(
    val name: String? = null,
    val url: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("proxy_icon_url") val proxyIconUrl: String? = null,
)
