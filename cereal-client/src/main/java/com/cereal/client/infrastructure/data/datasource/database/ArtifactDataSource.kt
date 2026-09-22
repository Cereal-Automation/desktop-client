package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Persists [Artifact] metadata for a user. The artifact's bytes live on disk (see
 * [com.cereal.client.infrastructure.data.datasource.filesystem.ArtifactFileDataSource]); this source stores only the
 * row, including the [relativePath] used to locate those bytes.
 */
interface ArtifactDataSource {
    suspend fun insert(
        user: User,
        artifact: Artifact,
        relativePath: String,
    )

    fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<Artifact>>

    /** Returns the on-disk relative path stored for [artifactId], or null if no such artifact exists. */
    suspend fun getRelativePath(
        user: User,
        artifactId: String,
    ): String?

    suspend fun deleteForTask(
        user: User,
        taskId: String,
    )
}
