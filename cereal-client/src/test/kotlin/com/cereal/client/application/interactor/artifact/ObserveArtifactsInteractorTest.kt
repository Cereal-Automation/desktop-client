package com.cereal.client.application.interactor.artifact

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveArtifactsInteractorTest {
    @Test
    fun `run should emit artifacts emitted for the task`() =
        runTest {
            val repository = InMemoryArtifactRepository()
            val interactor = ObserveArtifactsInteractor(repository)
            repository.emit("task-1", "report.json", "{}".toByteArray(), "application/json")

            val artifacts = interactor.run(ObserveArtifactsInteractor.Params("task-1")).first()

            assertEquals(1, artifacts.size)
            assertEquals("report.json", artifacts.first().name)
            assertEquals("application/json", artifacts.first().mimeType)
        }

    @Test
    fun `run should not emit artifacts belonging to another task`() =
        runTest {
            val repository = InMemoryArtifactRepository()
            val interactor = ObserveArtifactsInteractor(repository)
            repository.emit("task-other", "other.csv", "a".toByteArray(), null)

            val artifacts = interactor.run(ObserveArtifactsInteractor.Params("task-1")).first()

            assertEquals(0, artifacts.size)
        }
}
