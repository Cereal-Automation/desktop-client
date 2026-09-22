package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Release(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Long,
    @SerialName("release_notes") val releaseNotes: String?,
)
