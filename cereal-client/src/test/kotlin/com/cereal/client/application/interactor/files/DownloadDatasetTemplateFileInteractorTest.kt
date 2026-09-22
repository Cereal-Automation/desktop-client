package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDatasetFileProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class DownloadDatasetTemplateFileInteractorTest {
    @Test
    fun `should complete when repository saves the template`() =
        runTest {
            val repository = InMemoryDatasetFileProvider()
            val interactor = DownloadDatasetTemplateFileInteractor(repository)

            interactor.run(
                DownloadDatasetTemplateFileInteractor.Params(
                    datasetType = DatasetType.Proxy,
                    file = File("/tmp/template.txt"),
                ),
            )
        }

    @Test
    fun `should propagate exception when repository save fails`() =
        runTest {
            val repository = mockk<DatasetFileProvider>()
            every { repository.saveTemplate(any(), any()) } throws RuntimeException("Save error")
            val interactor = DownloadDatasetTemplateFileInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(
                    DownloadDatasetTemplateFileInteractor.Params(
                        datasetType = DatasetType.Proxy,
                        file = File("/tmp/template.txt"),
                    ),
                )
            }
        }
}
