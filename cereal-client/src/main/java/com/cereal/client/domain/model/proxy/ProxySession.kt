package com.cereal.client.domain.model.proxy

/**
 * How a synced proxy endpoint behaves between requests.
 *
 * - [ROTATING]: a new exit IP is assigned per request. A single rotating endpoint represents the whole
 *   pool, so only one rotating connection string is ever stored.
 * - [STICKY]: the exit IP is held for a fixed lifetime (30 minutes), so the user pulls many distinct
 *   sticky endpoints to spread work across IPs.
 */
enum class ProxySession {
    ROTATING,
    STICKY,
}
