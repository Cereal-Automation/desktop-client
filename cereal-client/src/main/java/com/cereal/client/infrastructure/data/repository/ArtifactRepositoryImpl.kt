package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ArtifactDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ArtifactFileDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ArtifactRepositoryImpl(
    private val artifactDataSource: ArtifactDataSource,
    private val artifactFileDataSource: ArtifactFileDataSource,
    private val userSession: UserSession,
) : ArtifactRepository {
    @OptIn(ExperimentalTime::class)
    override suspend fun emit(
        taskId: String,
        name: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        val user = userSession.requireUser()
        val id = UUID.randomUUID().toString()
        val relativePath = "$taskId/$id"

        // Write the bytes first, then the row. A failure before the row is inserted leaves an orphan file
        // (invisible to the UI, removed when the task is deleted) rather than a row pointing at missing bytes.
        artifactFileDataSource.write(user, relativePath, bytes)
        artifactDataSource.insert(
            user = user,
            artifact =
                Artifact(
                    id = id,
                    taskId = taskId,
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                    createdAt = Clock.System.now(),
                ),
            relativePath = relativePath,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeArtifacts(taskId: String): Flow<List<Artifact>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                artifactDataSource.observeByTaskId(user, taskId)
            }
        }

    override suspend fun writeToFile(
        artifactId: String,
        destination: File,
    ) {
        val user = userSession.requireUser()
        val relativePath =
            artifactDataSource.getRelativePath(user, artifactId)
                ?: error("No artifact with id $artifactId found.")
        artifactFileDataSource.copyToFile(user, relativePath, destination)
    }

    override suspend fun deleteForTask(taskId: String) {
        val user = userSession.requireUser()
        artifactDataSource.deleteForTask(user, taskId)
        artifactFileDataSource.deleteForTask(user, taskId)
    }
}
