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
 * Room entity for dataset groups
 */
@Entity(tableName = "dataset_group")
data class DatasetGroupEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        val name: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for dataset group item definitions
 */
@Entity(
    tableName = "dataset_group_item_definition",
    foreignKeys = [
        ForeignKey(
            entity = DatasetGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
    ],
)
data class DatasetGroupItemDefinitionEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "group_id")
        val groupId: UUID,
        val key: String,
        val name: String,
        val description: String,
        val type: ValueType,
        val position: Int,
        @ColumnInfo(name = "is_nullable")
        val isNullable: Boolean,
        @ColumnInfo(name = "is_script_identifier")
        val isScriptIdentifier: Boolean,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for datasets
 */
@Entity(
    tableName = "dataset",
    foreignKeys = [
        ForeignKey(
            entity = DatasetGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
    ],
)
data class DatasetEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "group_id")
        val groupId: UUID,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for dataset items
 */
@Entity(
    tableName = "dataset_item",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["dataset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["dataset_id"]),
    ],
)
data class DatasetItemEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "dataset_id")
        val datasetId: UUID,
        val key: String,
        val value: EncryptedString,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )
