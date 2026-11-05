package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/** Persists [RunTrace] metadata for a user; the bytes live on disk under the trace's task and id. */
interface RunTraceDataSource {
    suspend fun insert(
        user: User,
        trace: RunTrace,
    )

    suspend fun getAll(user: User): List<RunTrace>

    /** Replaces the row with [trace]'s id. */
    suspend fun update(
        user: User,
        trace: RunTrace,
    )

    fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<RunTrace>>

    suspend fun delete(
        user: User,
        ids: List<String>,
    )

    suspend fun deleteForTask(
        user: User,
        taskId: String,
    )
}
