package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetSdkVersionInteractorTest {
    private lateinit var applicationRepository: InMemoryApplicationRepository
    private lateinit var interactor: GetSdkVersionInteractor

    @BeforeEach
    fun setUp() {
        applicationRepository = InMemoryApplicationRepository()
        interactor = GetSdkVersionInteractor(applicationRepository)
    }

    @Test
    fun `run returns the SDK version reported by the application repository`() =
        runTest {
            val result = interactor.run(Interactor.None())

            assertEquals(SemVer(1, 0, 0), result)
        }
}
