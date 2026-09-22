package com.cereal.client.domain.model.proxy

/**
 * Live account summary returned by a proxy provider's account endpoint (MarsProxies `/v1/residential/me`).
 *
 * @property availableTrafficGb Remaining residential traffic balance, in gigabytes.
 * @property subUserCount Number of sub-users on the account.
 * @property accountHash Provider-side account identifier hash.
 */
data class ProxyProviderAccount(
    val availableTrafficGb: Double,
    val subUserCount: Int,
    val accountHash: String,
) {
    init {
        require(availableTrafficGb >= 0) { "Available traffic must not be negative" }
        require(subUserCount >= 0) { "Sub-user count must not be negative" }
    }
}
