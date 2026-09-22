package com.cereal.client.infrastructure.data.datasource.network.marketplace.request

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordRequestBody(
    val email: String,
)
