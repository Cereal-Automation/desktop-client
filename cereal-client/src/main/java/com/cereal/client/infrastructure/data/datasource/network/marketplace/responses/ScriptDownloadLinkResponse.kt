package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScriptDownloadLinkResponse(
    @SerialName("download_link") val downloadLink: String,
)
