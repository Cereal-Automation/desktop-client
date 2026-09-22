package com.cereal.client.infrastructure.data.datasource.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LatestAppVersionJsonResponse(
    val version: String,
    @SerialName("min_version") val minVersion: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("store_url") val storeUrl: String? = null,
    /**
     * Lowercase hex SHA-256 of the installer referenced by [downloadUrl]. Required for direct
     * downloads so the client can reject a tampered binary (see issue #484).
     */
    @SerialName("download_sha256") val downloadSha256: String? = null,
    /**
     * Base64 RSA-SHA256 signature over the canonical release message
     * (`version|min_version|download_url|download_sha256`), produced by the release pipeline with
     * the private counterpart of the embedded public key. Verified before the metadata is trusted.
     */
    @SerialName("download_signature") val downloadSignature: String? = null,
)
