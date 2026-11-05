package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.repository.RunTraceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** In-memory [RunTraceRepository] backing the `mock` flavor and doubling as a test fake. */
class InMemoryRunTraceRepository : RunTraceRepository {
    private val traces = MutableStateFlow<List<RunTrace>>(emptyList())

    /** The bytes kept, by trace id. */
    val bytes = ConcurrentHashMap<String, ByteArray>()

    override suspend fun add(
        trace: RunTrace,
        bytes: ByteArray,
    ) {
        this.bytes[trace.id] = bytes
        traces.update { it + trace }
    }

    override suspend fun getAll(): List<RunTrace> = traces.value

    override suspend fun readBytes(trace: RunTrace): ByteArray? = bytes[trace.id]

    override suspend fun update(trace: RunTrace) = traces.update { all -> all.map { if (it.id == trace.id) trace else it } }

    override suspend fun deleteBytes(trace: RunTrace) {
        bytes.remove(trace.id)
    }

    override fun observe(taskId: String): Flow<List<RunTrace>> = traces.map { all -> all.filter { it.taskId == taskId } }

    override suspend fun delete(traces: List<RunTrace>) {
        val ids = traces.map { it.id }.toSet()
        bytes.keys.removeAll(ids)
        this.traces.update { all -> all.filterNot { it.id in ids } }
    }

    override suspend fun deleteForTask(taskId: String) = delete(traces.value.filter { it.taskId == taskId })
}
