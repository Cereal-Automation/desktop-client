package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ArtifactComponentImplTest {
    @Test
    fun `emit should forward the artifact to the repository scoped to the task`() =
        runTest {
            val repository = InMemoryArtifactRepository()
            val component = ArtifactComponentImpl(repository, taskId = "task-1")

            component.emit("export.csv", "a,b".toByteArray(), "text/csv")

            val artifacts = repository.observeArtifacts("task-1").first()
            assertEquals(1, artifacts.size)
            assertEquals("export.csv", artifacts.first().name)
        }

    @Test
    fun `emit should propagate a persistence failure to the caller`() =
        runTest {
            val failing =
                object : ArtifactRepository {
                    override suspend fun emit(
                        taskId: String,
                        name: String,
                        bytes: ByteArray,
                        mimeType: String?,
                    ): Unit = error("disk full")

                    override fun observeArtifacts(taskId: String): Flow<List<Artifact>> = throw UnsupportedOperationException()

                    override suspend fun writeToFile(
                        artifactId: String,
                        destination: File,
                    ) = throw UnsupportedOperationException()

                    override suspend fun deleteForTask(taskId: String) = throw UnsupportedOperationException()
                }
            val component = ArtifactComponentImpl(failing, taskId = "task-1")

            assertThrows<IllegalStateException> {
                component.emit("export.csv", "a".toByteArray(), null)
            }
        }
}
