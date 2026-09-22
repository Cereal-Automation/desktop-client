package com.cereal.client.application.interactor.artifact

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SaveArtifactToFileInteractorTest {
    @Test
    fun `run should write the artifact bytes to the destination file`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryArtifactRepository()
        val bytes = "downloadable-content".toByteArray()
        repository.emit("task-1", "result.txt", bytes, null)
        val artifactId =
            repository
                .observeArtifacts("task-1")
                .first()
                .single()
                .id
        val interactor = SaveArtifactToFileInteractor(repository)
        val destination = File(tempDir, "result.txt")

        interactor.run(SaveArtifactToFileInteractor.Params(artifactId, destination))

        assertArrayEquals(bytes, destination.readBytes())
    }
}
