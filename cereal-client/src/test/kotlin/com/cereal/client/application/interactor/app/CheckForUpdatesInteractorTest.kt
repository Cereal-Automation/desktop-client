package com.cereal.client.application.interactor.app

import com.cereal.client.application.Interactor
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.app.VersionCheckService
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAppUpdateProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckForUpdatesInteractorTest {
    @Test
    fun `run reports up to date when installed version matches the latest`() =
        runTest {
            // Real VersionCheckService over the in-memory repository + provider (installed == latest == 1.0.0).
            val interactor =
                CheckForUpdatesInteractor(
                    VersionCheckService(InMemoryApplicationRepository(), InMemoryAppUpdateProvider()),
                )

            val result = interactor.run(Interactor.None())

            assertEquals(UpdateCheckResult.UpToDate, result)
        }
}
