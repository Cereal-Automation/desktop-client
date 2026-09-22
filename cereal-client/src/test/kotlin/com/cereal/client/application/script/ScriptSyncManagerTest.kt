package com.cereal.client.application.script

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.ScriptRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScriptSyncManagerTest {
    private val authProvider: AuthProvider = mockk(relaxed = true)
    private val scriptRepository: ScriptRepository = mockk(relaxed = true)
    private val scriptInstallProvider: ScriptInstallProvider = mockk(relaxed = true)
    private val scriptManager: ScriptManager = mockk(relaxed = true)
    private val applicationPreferenceRepository: ApplicationPreferenceRepository = mockk(relaxed = true)
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)

    private val syncManager =
        ScriptSyncManager(
            authProvider = authProvider,
            scriptRepository = scriptRepository,
            scriptInstallProvider = scriptInstallProvider,
            scriptManager = scriptManager,
            applicationPreferenceRepository = applicationPreferenceRepository,
            applicationConfig = applicationConfig,
        )

    private fun listing(publicIdentifier: String) =
        ScriptEntitlement(
            publicIdentifier = publicIdentifier,
            title = publicIdentifier,
            latestRelease = Release(versionName = "1.0.0", versionCode = 1, releaseNotes = null),
            latestDraftRelease = null,
            shortDescription = null,
            price = null,
        )

    private fun installedPackage(packageName: String): ScriptPackage {
        val manifest = mockk<com.cereal.client.domain.model.script.Manifest>()
        every { manifest.packageName } returns packageName
        val scriptPackage = mockk<ScriptPackage>()
        every { scriptPackage.manifest } returns manifest
        return scriptPackage
    }

    private fun stubSubscriptions(vararg publicIdentifiers: String) {
        coEvery { applicationPreferenceRepository.isDevelopmentScriptsEnabled() } returns flowOf(false)
        coEvery { authProvider.getSubscriptions(any()) } returns
            publicIdentifiers.map { Subscription(id = "sub-$it", entitlement = listing(it)) }
        coEvery { scriptInstallProvider.installScript(any(), any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `branded build only installs the brand scripts`() =
        runTest {
            every { applicationConfig.isBranded } returns true
            every { applicationConfig.brandScriptIds } returns listOf("com.brand.a")
            stubSubscriptions("com.brand.a", "com.other.b")
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(emptyList())

            syncManager.sync()

            coVerify(exactly = 1) { scriptInstallProvider.installScript("com.brand.a", any()) }
            coVerify(exactly = 0) { scriptInstallProvider.installScript("com.other.b", any()) }
        }

    @Test
    fun `branded build removes an installed non-brand script`() =
        runTest {
            every { applicationConfig.isBranded } returns true
            every { applicationConfig.brandScriptIds } returns listOf("com.brand.a")
            stubSubscriptions("com.brand.a")
            val foreign = installedPackage("com.other.b")
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(listOf(foreign))

            syncManager.sync()

            coVerify(exactly = 1) { scriptManager.deleteScript(foreign) }
        }

    @Test
    fun `stock build installs every subscribed script`() =
        runTest {
            every { applicationConfig.isBranded } returns false
            stubSubscriptions("com.a", "com.b")
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(emptyList())

            syncManager.sync()

            coVerify(exactly = 1) { scriptInstallProvider.installScript("com.a", any()) }
            coVerify(exactly = 1) { scriptInstallProvider.installScript("com.b", any()) }
        }

    @Test
    fun `syncWithProgress emits a monotonic count up to the total and a terminal finished`() =
        runTest {
            every { applicationConfig.isBranded } returns false
            stubSubscriptions("com.a", "com.b")
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(emptyList())

            val emitted = syncManager.syncWithProgress().toList()

            // Initial (total unknown), total resolved, one per script, then terminal.
            assertEquals(
                listOf(
                    SyncProgress.InProgress(completed = 0, total = 0),
                    SyncProgress.InProgress(completed = 0, total = 2),
                    SyncProgress.InProgress(completed = 1, total = 2),
                    SyncProgress.InProgress(completed = 2, total = 2),
                ),
                emitted.dropLast(1),
            )
            assertEquals(SyncProgress.Finished(emptyMap()), emitted.last())
        }

    @Test
    fun `syncWithProgress still reaches the total when a script fails to install`() =
        runTest {
            every { applicationConfig.isBranded } returns false
            stubSubscriptions("com.a", "com.b")
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(emptyList())
            val failure = RuntimeException("download failed")
            coEvery { scriptInstallProvider.installScript("com.a", any()) } throws failure

            val emitted = syncManager.syncWithProgress().toList()

            // The failed script is best-effort: the count still advances to the total.
            assertEquals(SyncProgress.InProgress(completed = 2, total = 2), emitted.dropLast(1).last())
            val finished = emitted.last() as SyncProgress.Finished
            assertEquals(setOf("com.a"), finished.exceptions.keys)
        }
}
