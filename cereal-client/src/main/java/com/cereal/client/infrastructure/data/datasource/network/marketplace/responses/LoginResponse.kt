package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val user: User,
)
