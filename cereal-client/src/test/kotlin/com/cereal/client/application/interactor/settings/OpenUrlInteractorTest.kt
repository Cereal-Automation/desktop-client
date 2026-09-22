package com.cereal.client.application.interactor.settings

import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenUrlInteractorTest {
    private lateinit var systemRepository: InMemorySystemProvider
    private lateinit var interactor: OpenUrlInteractor

    @BeforeEach
    fun setUp() {
        systemRepository = InMemorySystemProvider()
        interactor = OpenUrlInteractor(systemRepository)
    }

    @Test
    fun `run opens the given url in the browser`() =
        runTest {
            interactor.run(OpenUrlInteractor.Params("https://example.com"))

            assertEquals(listOf("https://example.com"), systemRepository.browsedUrls)
        }
}
