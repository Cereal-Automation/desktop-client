package com.cereal.client.domain.repository

import com.cereal.client.domain.model.artifact.Artifact
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Owns the lifecycle of script-emitted [Artifact]s: persisting them against a task, observing them, writing them
 * out for download, and removing them when their task is deleted.
 */
interface ArtifactRepository {
    /**
     * Persists a new artifact for [taskId]. Append-only: each call stores a distinct artifact and never replaces a
     * previous one. Throws if the artifact cannot be persisted.
     */
    suspend fun emit(
        taskId: String,
        name: String,
        bytes: ByteArray,
        mimeType: String?,
    )

    /** Emits the current list of artifacts for [taskId] and re-emits whenever that set changes. */
    fun observeArtifacts(taskId: String): Flow<List<Artifact>>

    /** Decrypts the artifact identified by [artifactId] and writes its bytes to [destination]. */
    suspend fun writeToFile(
        artifactId: String,
        destination: File,
    )

    /** Removes all persisted artifacts and on-disk files belonging to [taskId]. */
    suspend fun deleteForTask(taskId: String)
}
