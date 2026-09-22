package com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of `GET /v1/residential/me`. A 200 means the token is valid.
 *
 * DTOs are high-confidence but provisional (derived from public docs, not a live call); the data-source
 * seam and MockWebServer fixtures are where any drift surfaces.
 */
@Serializable
data class MarsProxiesAccountResponse(
    @SerialName("traffic_available")
    val trafficAvailable: Double,
    @SerialName("subusers_count")
    val subUsersCount: Int,
    @SerialName("hash")
    val hash: String,
)
