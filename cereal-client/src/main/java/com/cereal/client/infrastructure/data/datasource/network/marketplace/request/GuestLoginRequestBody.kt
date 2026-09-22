package com.cereal.client.infrastructure.data.datasource.network.marketplace.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuestLoginRequestBody(
    @SerialName("device_name") val deviceName: String,
)
