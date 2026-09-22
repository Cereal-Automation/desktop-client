package com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /v1/residential/subusers`. */
@Serializable
data class MarsProxiesSubUsersResponse(
    @SerialName("data")
    val data: List<MarsProxiesSubUserDto> = emptyList(),
)

@Serializable
data class MarsProxiesSubUserDto(
    @SerialName("id")
    val id: String,
    @SerialName("hash")
    val hash: String,
    @SerialName("username")
    val username: String,
    @SerialName("password")
    val password: String,
    @SerialName("traffic_available")
    val trafficAvailable: Double,
    @SerialName("traffic_used")
    val trafficUsed: Double,
)
