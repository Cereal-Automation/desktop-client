package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room entity for application settings
 */
@Entity(tableName = "application_settings")
data class SettingsEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        val key: String,
        val value: EncryptedString,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )
