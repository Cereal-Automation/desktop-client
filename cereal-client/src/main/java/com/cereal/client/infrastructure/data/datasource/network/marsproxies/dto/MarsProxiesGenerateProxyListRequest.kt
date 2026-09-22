package com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body of `POST /v1/residential/access/generate-proxy-list`.
 *
 * The endpoint renders [proxyCount] connection strings using [format] as a template, encoding the
 * gateway ([hostname]:[port]), rotation mode, optional sticky [lifetime], the resolved [subUserHash],
 * and optional [location] targeting. The response is a JSON array of rendered connection strings.
 *
 * DTOs are high-confidence but provisional (derived from public docs, not a live call); the data-source
 * seam and MockWebServer fixtures are where any drift surfaces.
 *
 * @property format Connection-string template, e.g. "{hostname}:{port}:{username}:{password}".
 * @property hostname Gateway host (e.g. "ultra.marsproxies.com").
 * @property port Gateway port (the named "http" port, 44443).
 * @property rotation "rotating" or "sticky".
 * @property lifetime Sticky session lifetime (e.g. "30m"); omitted for rotating.
 * @property subUserHash Hash of the resolved sub-user.
 * @property location Composed geo-targeting string; omitted when no targeting is requested.
 * @property proxyCount Number of endpoints to render; omitted for rotating (a single endpoint is kept).
 */
@Serializable
data class MarsProxiesGenerateProxyListRequest(
    @SerialName("format")
    val format: String,
    @SerialName("hostname")
    val hostname: String,
    @SerialName("port")
    val port: Int,
    @SerialName("rotation")
    val rotation: String,
    @SerialName("lifetime")
    val lifetime: String? = null,
    @SerialName("subuser_hash")
    val subUserHash: String,
    @SerialName("location")
    val location: String? = null,
    @SerialName("proxy_count")
    val proxyCount: Int? = null,
)
