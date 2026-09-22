package com.cereal.client.infrastructure.data.notification.discord.serializable

import kotlinx.serialization.Serializable

/**
 * Serializable version of FieldEmbed for JSON serialization.
 */
@Serializable
data class SerializableFieldEmbed(
    val name: String,
    val value: String,
    val inline: Boolean,
)
