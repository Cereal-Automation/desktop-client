package com.cereal.client.domain.repository

import com.cereal.client.domain.model.diagnostics.RunTrace
import kotlinx.coroutines.flow.Flow

/** Keeps [RunTrace]s for the signed-in user: a metadata row each, with the trace's bytes encrypted on disk. */
interface RunTraceRepository {
    suspend fun add(
        trace: RunTrace,
        bytes: ByteArray,
    )

    suspend fun getAll(): List<RunTrace>

    /** [trace]'s bytes; null once they're gone. */
    suspend fun readBytes(trace: RunTrace): ByteArray?

    /** Replaces the row of [trace] (same id), leaving its bytes as they are. */
    suspend fun update(trace: RunTrace)

    /** Removes [trace]'s bytes but keeps its row. */
    suspend fun deleteBytes(trace: RunTrace)

    /** Emits [taskId]'s traces and re-emits whenever they change. */
    fun observe(taskId: String): Flow<List<RunTrace>>

    /** Removes [traces], rows and bytes. */
    suspend fun delete(traces: List<RunTrace>)

    /** Removes every trace of [taskId], rows and bytes. */
    suspend fun deleteForTask(taskId: String)
}
