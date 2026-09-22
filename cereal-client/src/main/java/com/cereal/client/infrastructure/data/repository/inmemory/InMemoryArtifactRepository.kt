package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.repository.ArtifactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * In-memory [ArtifactRepository] backing the `mock` flavor and doubling as a test fake. Bytes are held in memory and
 * written verbatim to the destination on [writeToFile].
 */
class InMemoryArtifactRepository : ArtifactRepository {
    private val artifacts = mutableMapOf<String, MutableStateFlow<List<Artifact>>>()
    private val bytesById = mutableMapOf<String, ByteArray>()

    @OptIn(ExperimentalTime::class)
    override suspend fun emit(
        taskId: String,
        name: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        val id = UUID.randomUUID().toString()
        bytesById[id] = bytes
        val artifact =
            Artifact(
                id = id,
                taskId = taskId,
                name = name,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                createdAt = Clock.System.now(),
            )
        flowFor(taskId).update { it + artifact }
    }

    override fun observeArtifacts(taskId: String): Flow<List<Artifact>> = flowFor(taskId)

    override suspend fun writeToFile(
        artifactId: String,
        destination: File,
    ) {
        val bytes = bytesById[artifactId] ?: error("No artifact with id $artifactId found.")
        destination.parentFile?.mkdirs()
        destination.writeBytes(bytes)
    }

    override suspend fun deleteForTask(taskId: String) {
        val removed = flowFor(taskId).value
        removed.forEach { bytesById.remove(it.id) }
        flowFor(taskId).value = emptyList()
    }

    private fun flowFor(taskId: String) = artifacts.getOrPut(taskId) { MutableStateFlow(emptyList()) }
}
