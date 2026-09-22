package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.ApplicationRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStream

class ScriptInstallProviderImplTest {
    private val fileSystemScriptsDataSource = mockk<FileSystemScriptsDataSource>()
    private val marketplaceDataSource = mockk<MarketplaceDataSource>()
    private val userSession = mockk<UserSession>()
    private val subscriptionDataSource = mockk<SubscriptionDataSource>()
    private val applicationRepository = mockk<ApplicationRepository>()
    private lateinit var provider: ScriptInstallProviderImpl

    @BeforeEach
    fun setUp() {
        coEvery { applicationRepository.getSdkVersion() } returns SemVer(1, 0, 0)
        provider =
            ScriptInstallProviderImpl(
                fileSystemScriptsDataSource = fileSystemScriptsDataSource,
                marketplaceDataSource = marketplaceDataSource,
                userSession = userSession,
                subscriptionDataSource = subscriptionDataSource,
                applicationRepository = applicationRepository,
            )
    }

    @Test
    fun `installScript should invalidate cache, fetch subscriptions, and return ScriptPackage with supportUrl`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val release = Release(versionName = "1.0", versionCode = 1L, releaseNotes = null)
            val definition = aScriptPackageDefinition("com.example.script")
            val inputStream = mockk<InputStream>(relaxed = true)
            coEvery { marketplaceDataSource.downloadScript("com.example.script", 1L) } returns inputStream
            coEvery {
                fileSystemScriptsDataSource.storeScript("com.example.script", release, inputStream, user, any())
            } returns definition
            coEvery { subscriptionDataSource.invalidateCache() } returns Unit
            coEvery { subscriptionDataSource.getSubscriptions() } returns
                listOf(aSubscription("com.example.script", "https://github.com/example/issues"))

            val result = provider.installScript("com.example.script", release)

            coVerify(exactly = 1) { subscriptionDataSource.invalidateCache() }
            coVerify(exactly = 1) { subscriptionDataSource.getSubscriptions() }
            assertEquals("com.example.script", result.manifest.packageName)
            assertEquals("https://github.com/example/issues", result.manifest.supportUrl)
        }

    @Test
    fun `installScript should return ScriptPackage with null supportUrl when no matching subscription`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val release = Release(versionName = "1.0", versionCode = 1L, releaseNotes = null)
            val definition = aScriptPackageDefinition("com.example.free-script")
            val inputStream = mockk<InputStream>(relaxed = true)
            coEvery { marketplaceDataSource.downloadScript("com.example.free-script", 1L) } returns inputStream
            coEvery {
                fileSystemScriptsDataSource.storeScript("com.example.free-script", release, inputStream, user, any())
            } returns definition
            coEvery { subscriptionDataSource.invalidateCache() } returns Unit
            coEvery { subscriptionDataSource.getSubscriptions() } returns emptyList()

            val result = provider.installScript("com.example.free-script", release)

            assertNull(result.manifest.supportUrl)
        }

    @Test
    fun `updateScript should preserve supportUrl from input ScriptPackage`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val release = Release(versionName = "2.0", versionCode = 2L, releaseNotes = null)
            val existingPackage =
                aScriptPackage("com.example.script").let {
                    it.copy(manifest = it.manifest.copy(supportUrl = "https://github.com/example/issues"))
                }
            val definition = aScriptPackageDefinition("com.example.script")
            val inputStream = mockk<InputStream>(relaxed = true)
            coEvery { marketplaceDataSource.downloadScript("com.example.script", 2L) } returns inputStream
            coEvery {
                fileSystemScriptsDataSource.updateScript(existingPackage, release, inputStream, user, any())
            } returns definition

            val result = provider.updateScript(existingPackage, release)

            assertEquals("https://github.com/example/issues", result.manifest.supportUrl)
        }

    @Test
    fun `updateScript should preserve null supportUrl for free scripts`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val release = Release(versionName = "2.0", versionCode = 2L, releaseNotes = null)
            val existingPackage = aScriptPackage("com.example.free-script")
            val definition = aScriptPackageDefinition("com.example.free-script")
            val inputStream = mockk<InputStream>(relaxed = true)
            coEvery { marketplaceDataSource.downloadScript("com.example.free-script", 2L) } returns inputStream
            coEvery {
                fileSystemScriptsDataSource.updateScript(existingPackage, release, inputStream, user, any())
            } returns definition

            val result = provider.updateScript(existingPackage, release)

            assertNull(result.manifest.supportUrl)
        }

    private fun aScriptPackage(packageName: String) =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(),
            childScripts = emptyMap(),
        )

    private fun aScriptPackageDefinition(packageName: String) =
        ScriptPackageDefinition(
            source = File("/tmp/fake.jar"),
            manifest =
                ManifestDefinition(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(),
            childScripts = emptyMap(),
        )

    private fun aSubscription(
        packageName: String,
        supportUrl: String?,
    ) = Subscription(
        id = "sub-1",
        entitlement =
            ScriptEntitlement(
                publicIdentifier = packageName,
                title = "Test Script",
                latestRelease = null,
                latestDraftRelease = null,
                shortDescription = null,
                price = null,
                supportUrl = supportUrl,
            ),
    )
}
