package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ArtifactDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

class RoomArtifactDataSource(
    private val roomDatabases: RoomDatabases,
) : ArtifactDataSource {
    @OptIn(ExperimentalTime::class)
    override suspend fun insert(
        user: User,
        artifact: Artifact,
        relativePath: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            userDatabase.artifactDao().insert(
                ArtifactEntity(
                    id = artifact.id,
                    taskId = artifact.taskId,
                    name = artifact.name,
                    mimeType = artifact.mimeType,
                    sizeBytes = artifact.sizeBytes,
                    relativePath = relativePath,
                    createdAt = artifact.createdAt,
                ),
            )
        }
    }

    override fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<Artifact>> =
        roomDatabases
            .getUserDatabase(user)
            .artifactDao()
            .observeByTaskId(taskId)
            .map { entities -> entities.map { it.toArtifact() } }

    override suspend fun getRelativePath(
        user: User,
        artifactId: String,
    ): String? =
        roomDatabases
            .getUserDatabase(user)
            .artifactDao()
            .getById(artifactId)
            ?.relativePath

    override suspend fun deleteForTask(
        user: User,
        taskId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            userDatabase.artifactDao().deleteByTaskId(taskId)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun ArtifactEntity.toArtifact() =
        Artifact(
            id = id,
            taskId = taskId,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
        )
}
