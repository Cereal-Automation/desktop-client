package com.cereal.client.presentation.tasks.model

import com.cereal.client.domain.model.task.Task
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class TaskUiModel(
    val id: Task,
    val taskNumber: Int,
    val isRunning: Boolean,
    val isError: Boolean,
    val isSuccess: Boolean,
    val hasSupportUrl: Boolean,
    val message: String,
    val status: String,
    val configuration: String,
    val stackTrace: String?,
    /** When the current status began; null when the task has no status history yet. */
    val statusSince: Instant? = null,
)
