package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import com.cereal.client.infrastructure.data.datasource.network.marketplace.adapter.BigDecimalSerializer
import com.cereal.client.infrastructure.data.datasource.network.marketplace.adapter.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

@Serializable
data class MarketplaceScript(
    val id: String,
    @SerialName("public_identifier") val publicIdentifier: String,
    val title: String,
    val description: String? = null,
    @SerialName("description_short") val shortDescription: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal? = null,
    @SerialName("formatted_price") val formattedPrice: String? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("free_trial_enabled") val freeTrialEnabled: Boolean = false,
    @SerialName("free_trial_days") val freeTrialDays: Int? = null,
    @SerialName("support_url") val supportUrl: String? = null,
    @SerialName("average_rating") val averageRating: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("is_community") val isCommunity: Boolean = false,
    @SerialName("is_new") val isNew: Boolean = false,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean = false,
    @SerialName("latest_release") val latestRelease: Release? = null,
    val developer: ScriptOwner? = null,
    val tags: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class) @SerialName("created_at") val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) @SerialName("updated_at") val updatedAt: Instant? = null,
)
