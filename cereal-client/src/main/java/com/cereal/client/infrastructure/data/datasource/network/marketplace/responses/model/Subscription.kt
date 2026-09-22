package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val script: Script,
)
