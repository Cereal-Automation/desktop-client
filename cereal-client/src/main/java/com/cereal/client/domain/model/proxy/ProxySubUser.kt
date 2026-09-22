package com.cereal.client.domain.model.proxy

/**
 * A sub-user on a proxy provider account (MarsProxies `/v1/residential/subusers`).
 *
 * When connecting, the connector resolves the sub-user with the most [trafficAvailable] and caches its
 * [hash] on the connector record; the rest of the fields back later slices (proxy pulling).
 */
data class ProxySubUser(
    val id: String,
    val hash: String,
    val username: String,
    val password: String,
    val trafficAvailable: Double,
    val trafficUsed: Double,
) {
    init {
        require(hash.isNotBlank()) { "Sub-user hash must not be blank" }
        require(trafficAvailable >= 0) { "Available traffic must not be negative" }
        require(trafficUsed >= 0) { "Used traffic must not be negative" }
    }
}
