package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SubscribeStatus {
    @SerialName("subscribed")
    SUBSCRIBED,

    @SerialName("already_subscribed")
    ALREADY_SUBSCRIBED,

    @SerialName("checkout_initiated")
    CHECKOUT_INITIATED,
}

@Serializable
data class SubscribeScriptResponse(
    val status: SubscribeStatus,
    val message: String? = null,
    @SerialName("checkout_url") val checkoutUrl: String? = null,
)
