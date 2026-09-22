package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
    val errors: List<String>? = null,
    val status: String? = null,
)
