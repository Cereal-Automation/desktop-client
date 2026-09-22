package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScriptOwner(
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val verified: Boolean = false,
)
