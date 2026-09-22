package com.cereal.client.domain.model.proxy

/**
 * Where freshly-synced proxies are written.
 */
sealed interface SyncTarget {
    /** Create a brand-new proxy group with [name] and append the synced proxies to it. */
    data class NewGroup(
        val name: String,
    ) : SyncTarget {
        init {
            require(name.isNotBlank()) { "New group name must not be blank" }
        }
    }

    /** Append the synced proxies to the existing proxy group identified by [groupId]. */
    data class ExistingGroup(
        val groupId: String,
    ) : SyncTarget {
        init {
            require(groupId.isNotBlank()) { "Existing group id must not be blank" }
        }
    }
}

/**
 * A user-configured proxy pull from a provider.
 *
 * Geo targeting is composed from a bundled catalogue ([ProxyGeoCatalogue]): a required [country]
 * (ISO 3166-1 alpha-2), an optional US [state] (full name, only meaningful when [country] is "US"),
 * and an optional free-text [city]. [count] is only applied for [ProxySession.STICKY]; a rotating pull
 * always collapses to a single endpoint.
 *
 * @property country ISO 3166-1 alpha-2 country code (e.g. "US").
 * @property state Full US state name (e.g. "Texas"), or `null` for no state targeting.
 * @property city Free-text city name, or `null` for no city targeting.
 * @property session Rotating or sticky behaviour.
 * @property count Number of sticky endpoints to pull (10..2000). Ignored for rotating.
 * @property target Where the synced proxies are written.
 */
data class ProxySyncConfig(
    val country: String,
    val state: String? = null,
    val city: String? = null,
    val session: ProxySession = ProxySession.STICKY,
    val count: Int = DEFAULT_COUNT,
    val target: SyncTarget,
) {
    init {
        require(country.isNotBlank()) { "Country must not be blank" }
        require(count in MIN_COUNT..MAX_COUNT) { "Count must be between $MIN_COUNT and $MAX_COUNT" }
        state?.let { require(it.isNotBlank()) { "State must not be blank if provided" } }
        city?.let { require(it.isNotBlank()) { "City must not be blank if provided" } }
    }

    companion object {
        const val MIN_COUNT = 10
        const val MAX_COUNT = 2000
        const val DEFAULT_COUNT = 500
    }
}
