package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room entity for a connected proxy provider. Provider-keyed: one record per provider.
 *
 * Holds a credential *reference* ([credentialKey]) rather than the token, plus a snapshot of the account
 * summary ([availableTrafficGb], [subUserCount]) captured at connect/replace time so the connected card
 * can render without a network round-trip.
 */
@Entity(tableName = "proxy_provider_connector")
data class ProxyProviderConnectorEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val provider: String,
        @ColumnInfo(name = "connected_at")
        val connectedAt: Instant,
        @ColumnInfo(name = "last_sync_at")
        val lastSyncAt: Instant,
        @ColumnInfo(name = "subuser_hash")
        val subUserHash: String,
        @ColumnInfo(name = "available_traffic_gb")
        val availableTrafficGb: Double,
        @ColumnInfo(name = "subuser_count")
        val subUserCount: Int,
        @ColumnInfo(name = "credential_key")
        val credentialKey: String,
    )
