package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.repository.RunTraceRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.RunTraceDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ArtifactFileDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Rows in Room, bytes encrypted in the artifact format under the per-user diagnostics folder. */
class RunTraceRepositoryImpl(
    private val runTraceDataSource: RunTraceDataSource,
    private val diagnosticsFileDataSource: ArtifactFileDataSource,
    private val userSession: UserSession,
) : RunTraceRepository {
    override suspend fun add(
        trace: RunTrace,
        bytes: ByteArray,
    ) {
        val user = userSession.requireUser()
        // Bytes first, then the row: a failure in between leaves an orphan file the task's deletion removes.
        diagnosticsFileDataSource.write(user, trace.relativePath, bytes)
        runTraceDataSource.insert(user, trace)
    }

    override suspend fun getAll(): List<RunTrace> = runTraceDataSource.getAll(userSession.requireUser())

    override suspend fun readBytes(trace: RunTrace): ByteArray? = diagnosticsFileDataSource.read(userSession.requireUser(), trace.relativePath)

    override suspend fun update(trace: RunTrace) = runTraceDataSource.update(userSession.requireUser(), trace)

    override suspend fun deleteBytes(trace: RunTrace) = diagnosticsFileDataSource.delete(userSession.requireUser(), trace.relativePath)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(taskId: String): Flow<List<RunTrace>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else runTraceDataSource.observeByTaskId(user, taskId)
        }

    override suspend fun delete(traces: List<RunTrace>) {
        val user = userSession.requireUser()
        runTraceDataSource.delete(user, traces.map { it.id })
        traces.forEach { diagnosticsFileDataSource.delete(user, it.relativePath) }
    }

    override suspend fun deleteForTask(taskId: String) {
        val user = userSession.requireUser()
        runTraceDataSource.deleteForTask(user, taskId)
        diagnosticsFileDataSource.deleteForTask(user, taskId)
    }

    private val RunTrace.relativePath get() = "$taskId/$id"
}
