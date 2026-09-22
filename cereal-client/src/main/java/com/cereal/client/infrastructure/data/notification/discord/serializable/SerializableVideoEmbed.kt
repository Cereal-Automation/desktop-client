package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.Serializable

/**
 * Serializable version of VideoEmbed for JSON serialization.
 */
@Serializable
data class SerializableVideoEmbed(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null,
)
