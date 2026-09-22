package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room entity for proxies
 */
@Entity(
    tableName = "proxy",
    foreignKeys = [
        ForeignKey(
            entity = ProxyGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
    ],
)
data class ProxyEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "group_id")
        val groupId: UUID,
        val host: String,
        val port: Int,
        val username: String?,
        val password: EncryptedString?,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
        @ColumnInfo(name = "health_status")
        val healthStatus: String? = null,
        @ColumnInfo(name = "last_checked_at")
        val lastCheckedAt: Instant? = null,
        @ColumnInfo(name = "latency_ms")
        val latencyMs: Long? = null,
        @ColumnInfo(name = "last_error")
        val lastError: String? = null,
    )

/**
 * Room entity for proxy groups
 */
@Entity(tableName = "proxy_group")
data class ProxyGroupEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        val name: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
        // Provider this group was synced from (enum name), or null for manually-managed groups.
        @ColumnInfo(name = "provider")
        val provider: String? = null,
        // Human-readable geo targeting label of the last sync, or null.
        @ColumnInfo(name = "geo_label")
        val geoLabel: String? = null,
    )

/**
 * Data class for proxy group with proxy count
 * Used for queries that join proxy_group and proxy tables
 *
 * The proxy_count property mirrors the SQL column alias; renaming would break Room column mapping.
 */
@Suppress("ConstructorParameterNaming")
data class ProxyGroupWithCountEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: UUID,
        val name: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
        @ColumnInfo(name = "provider")
        val provider: String? = null,
        @ColumnInfo(name = "geo_label")
        val geoLabel: String? = null,
        val proxy_count: Long,
    )
