package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckScriptLicenseResponse(
    @SerialName("licensed") val isLicensed: Boolean,
    // Additive capacity fields, absent-tolerant. Parsed here for contract completeness; this DTO
    // (via MarketplaceApiClient.verifyLicense) currently has no caller — capacity for display and
    // start-time enforcement flows through the entitlement path (subscriptions / team scripts).
    val capacity: Int? = null,
    @SerialName("capacity_unit") val capacityUnit: String? = null,
)
