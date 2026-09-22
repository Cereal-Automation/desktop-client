package com.cereal.client.domain.model.proxy

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A persisted record of a connected proxy provider.
 *
 * The record holds a credential *reference* ([credentialKey]) — the name of the preference key under
 * which the sensitive token is stored — never the token itself. [availableTrafficGb] and [subUserCount]
 * are a snapshot of the account summary captured at connect/replace time so the connected card can be
 * rendered without a network round-trip.
 *
 * @property provider The connected provider (provider-keyed: one record per provider).
 * @property connectedAt When the provider was first connected.
 * @property lastSyncAt When the connector last refreshed account state (connect/replace time in this slice).
 * @property subUserHash Hash of the resolved sub-user (the one with the most available traffic).
 * @property availableTrafficGb Account traffic balance snapshot, in gigabytes.
 * @property subUserCount Account sub-user count snapshot.
 * @property credentialKey Reference to the preference key holding the sensitive token.
 */
@OptIn(ExperimentalTime::class)
data class ProxyProviderConnector(
    val provider: ProxyVendor,
    val connectedAt: Instant,
    val lastSyncAt: Instant,
    val subUserHash: String,
    val availableTrafficGb: Double,
    val subUserCount: Int,
    val credentialKey: String,
) {
    init {
        require(subUserHash.isNotBlank()) { "Sub-user hash must not be blank" }
        require(credentialKey.isNotBlank()) { "Credential key must not be blank" }
        require(availableTrafficGb >= 0) { "Available traffic must not be negative" }
        require(subUserCount >= 0) { "Sub-user count must not be negative" }
    }
}
