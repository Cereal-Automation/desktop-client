package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.datasets.DatasetFileInfo
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDatasetFileProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class OpenDatasetFileInteractorTest {
    @Test
    fun `should return file info from the repository`() =
        runTest {
            val repository = InMemoryDatasetFileProvider()
            val interactor = OpenDatasetFileInteractor(repository)

            val result =
                interactor.run(
                    OpenDatasetFileInteractor.Params(
                        type = DatasetType.Proxy,
                        file = File("/tmp/proxies.txt"),
                    ),
                )

            assertEquals(DatasetFileInfo(numberOfRecords = 0), result.fileInfo)
        }

    @Test
    fun `should return the record count produced by the repository`() =
        runTest {
            val repository = mockk<DatasetFileProvider>()
            every { repository.read(any(), any()) } returns DatasetFileInfo(numberOfRecords = 42)
            val interactor = OpenDatasetFileInteractor(repository)

            val result =
                interactor.run(
                    OpenDatasetFileInteractor.Params(
                        type = DatasetType.Proxy,
                        file = File("/tmp/proxies.txt"),
                    ),
                )

            assertEquals(42, result.fileInfo.numberOfRecords)
        }

    @Test
    fun `should propagate exception when repository read fails`() =
        runTest {
            val repository = mockk<DatasetFileProvider>()
            every { repository.read(any(), any()) } throws RuntimeException("Read error")
            val interactor = OpenDatasetFileInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(
                    OpenDatasetFileInteractor.Params(
                        type = DatasetType.Proxy,
                        file = File("/tmp/proxies.txt"),
                    ),
                )
            }
        }
}
