package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val key: String,
    @SerialName("is_guest") val isGuest: Boolean,
)
