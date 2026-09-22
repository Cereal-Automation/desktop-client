package com.cereal.client.application.interactor.settings

import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class OpenFileInteractorTest {
    private lateinit var systemRepository: InMemorySystemProvider
    private lateinit var interactor: OpenFileInteractor

    @BeforeEach
    fun setUp() {
        systemRepository = InMemorySystemProvider()
        interactor = OpenFileInteractor(systemRepository)
    }

    @Test
    fun `run opens the given file and returns the result`() =
        runTest {
            val file = File("/tmp/cereal-installer.dmg")
            systemRepository.openResult = OpenFileResult.Opened

            val result = interactor.run(OpenFileInteractor.Params(file))

            assertEquals(listOf(file), systemRepository.openedFiles)
            assertEquals(OpenFileResult.Opened, result)
        }

    @Test
    fun `run propagates the reveal result when the file cannot be opened`() =
        runTest {
            val file = File("/tmp/cereal-installer.deb")
            systemRepository.openResult = OpenFileResult.Revealed

            val result = interactor.run(OpenFileInteractor.Params(file))

            assertEquals(OpenFileResult.Revealed, result)
        }
}
