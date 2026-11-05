package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.RunTraceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.RunTraceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomRunTraceDataSource(
    private val roomDatabases: RoomDatabases,
) : RunTraceDataSource {
    override suspend fun insert(
        user: User,
        trace: RunTrace,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction { userDatabase.runTraceDao().insert(trace.toEntity()) }
    }

    override suspend fun getAll(user: User): List<RunTrace> =
        roomDatabases
            .getUserDatabase(user)
            .runTraceDao()
            .getAll()
            .map { it.toRunTrace() }

    override suspend fun update(
        user: User,
        trace: RunTrace,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction { userDatabase.runTraceDao().update(trace.toEntity()) }
    }

    override fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<RunTrace>> =
        roomDatabases
            .getUserDatabase(user)
            .runTraceDao()
            .observeByTaskId(taskId)
            .map { entities -> entities.map { it.toRunTrace() } }

    override suspend fun delete(
        user: User,
        ids: List<String>,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction { userDatabase.runTraceDao().deleteByIds(ids) }
    }

    override suspend fun deleteForTask(
        user: User,
        taskId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction { userDatabase.runTraceDao().deleteByTaskId(taskId) }
    }

    private fun RunTrace.toEntity() =
        RunTraceEntity(
            id = id,
            taskId = taskId,
            key = key,
            runId = runId,
            outcome = outcome,
            label = label,
            notable = notable,
            learned = learned,
            sizeBytes = sizeBytes,
            receivedAt = receivedAt,
            uploadState = uploadState.name,
            note = note,
        )

    private fun RunTraceEntity.toRunTrace() =
        RunTrace(
            id = id,
            taskId = taskId,
            key = key,
            runId = runId,
            outcome = outcome,
            label = label,
            notable = notable,
            learned = learned,
            sizeBytes = sizeBytes,
            receivedAt = receivedAt,
            uploadState = RunTrace.UploadState.valueOf(uploadState),
            note = note,
        )
}
