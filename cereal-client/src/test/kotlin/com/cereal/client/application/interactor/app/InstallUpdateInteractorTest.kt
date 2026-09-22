package com.cereal.client.application.interactor.app

import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class InstallUpdateInteractorTest {
    private val systemProvider = InMemorySystemProvider()
    private val interactor = InstallUpdateInteractor(systemProvider)

    @Test
    fun `run should install the update with the given installer file and expected sha`() =
        runTest {
            val installer = File("/tmp/cereal-update.AppImage")
            val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            systemProvider.installUpdateResult = UpdateInstallResult.Relaunching

            interactor.run(InstallUpdateInteractor.Params(installer, sha256))

            assertEquals(listOf(installer), systemProvider.installedUpdates)
            // The expected digest must reach the provider so it can re-verify before launch.
            assertEquals(listOf<String?>(sha256), systemProvider.installedUpdateSha256s)
        }

    @Test
    fun `run should return the result produced by the system provider`() =
        runTest {
            val installer = File("/tmp/cereal-update.dmg")
            systemProvider.installUpdateResult = UpdateInstallResult.Opened

            val result = interactor.run(InstallUpdateInteractor.Params(installer, expectedSha256 = null))

            assertEquals(UpdateInstallResult.Opened, result)
        }

    @Test
    fun `run should propagate exceptions thrown by the system provider`() =
        runTest {
            val installer = File("/tmp/cereal-update.AppImage")
            val throwingProvider =
                object : SystemProvider {
                    override suspend fun createTrayIcon() = Unit

                    override suspend fun browser(url: String) = Unit

                    override suspend fun open(directory: File): OpenFileResult = OpenFileResult.Opened

                    override suspend fun installUpdate(
                        installer: File,
                        expectedSha256: String?,
                    ): UpdateInstallResult = error("install failed")
                }
            val interactor = InstallUpdateInteractor(throwingProvider)

            assertThrows<IllegalStateException> {
                interactor.run(InstallUpdateInteractor.Params(installer, expectedSha256 = null))
            }
        }
}
