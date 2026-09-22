package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable version of FooterEmbed for JSON serialization.
 */
@Serializable
data class SerializableFooterEmbed(
    val text: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("proxy_icon_url") val proxyIconUrl: String? = null,
)
