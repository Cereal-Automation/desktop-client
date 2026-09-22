package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.Serializable

/**
 * Serializable version of ProviderEmbed for JSON serialization.
 */
@Serializable
data class SerializableProviderEmbed(
    val name: String? = null,
    val url: String? = null,
)
