package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One kept run trace's metadata; its bytes live encrypted under `Diagnostics/<userId>/<taskId>/<id>`. */
@Entity(
    tableName = "run_trace",
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
data class RunTraceEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: String,
        @ColumnInfo(name = "task_id")
        val taskId: String,
        val key: String,
        @ColumnInfo(name = "run_id")
        val runId: String?,
        val outcome: String,
        val label: String,
        val notable: Boolean,
        val learned: Boolean,
        @ColumnInfo(name = "size_bytes")
        val sizeBytes: Long,
        @ColumnInfo(name = "received_at")
        val receivedAt: Instant,
        @ColumnInfo(name = "upload_state")
        val uploadState: String,
        val note: String?,
    )
