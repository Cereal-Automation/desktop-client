package com.cereal.client.infrastructure.data.datasource.network.marketplace.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestBody(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)
