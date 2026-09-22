package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import com.cereal.client.infrastructure.data.datasource.network.marketplace.adapter.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Script(
    @SerialName("public_identifier") val publicIdentifier: String,
    val title: String,
    @SerialName("latest_release") val latestRelease: Release?,
    @SerialName("latest_draft_release") val latestDraftRelease: Release?,
    @SerialName("description_short") val shortDescription: String?,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal?,
    @SerialName("support_url") val supportUrl: String? = null,
    val capacity: Int? = null,
    @SerialName("capacity_unit") val capacityUnit: String? = null,
)
