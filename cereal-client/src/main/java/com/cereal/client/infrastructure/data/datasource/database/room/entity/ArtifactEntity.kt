package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Entity(
    tableName = "artifact",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["task_id"])],
)
data class ArtifactEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: String,
        @ColumnInfo(name = "task_id")
        val taskId: String,
        val name: String,
        @ColumnInfo(name = "mime_type")
        val mimeType: String?,
        @ColumnInfo(name = "size_bytes")
        val sizeBytes: Long,
        @ColumnInfo(name = "relative_path")
        val relativePath: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
    )
