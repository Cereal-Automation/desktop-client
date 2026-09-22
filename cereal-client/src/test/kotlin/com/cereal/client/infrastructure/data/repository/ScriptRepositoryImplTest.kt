package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ScriptRepositoryImplTest {
    private val fileSystemScriptsDataSource = mockk<FileSystemScriptsDataSource>()
    private val userSession = mockk<UserSession>()
    private val subscriptionDataSource = mockk<SubscriptionDataSource>()
    private lateinit var repository: ScriptRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository =
            ScriptRepositoryImpl(
                fileSystemScriptsDataSource = fileSystemScriptsDataSource,
                userSession = userSession,
                subscriptionDataSource = subscriptionDataSource,
            )
    }

    @Test
    fun `getInstalledScripts should overlay supportUrl from matching subscription`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val scriptPackage = aScriptPackageDefinition("com.example.script")
            coEvery { fileSystemScriptsDataSource.getScriptDefinitions(user) } returns flowOf(listOf(scriptPackage))
            coEvery { subscriptionDataSource.getSubscriptions() } returns
                listOf(
                    aSubscription("com.example.script", "https://example.com/support"),
                )

            val result = repository.getInstalledScripts().toList().first()

            assertEquals("https://example.com/support", result[0].manifest.supportUrl)
        }

    @Test
    fun `getInstalledScripts should set null supportUrl when no matching subscription`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val scriptPackage = aScriptPackageDefinition("com.example.script")
            coEvery { fileSystemScriptsDataSource.getScriptDefinitions(user) } returns flowOf(listOf(scriptPackage))
            coEvery { subscriptionDataSource.getSubscriptions() } returns emptyList()

            val result = repository.getInstalledScripts().toList().first()

            assertNull(result[0].manifest.supportUrl)
        }

    @Test
    fun `getScript should overlay supportUrl from matching subscription`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val scriptPackage = aScriptPackageDefinition("com.example.script")
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.script", user) } returns scriptPackage
            coEvery { subscriptionDataSource.getSubscriptions() } returns
                listOf(
                    aSubscription("com.example.script", "https://example.com/support"),
                )

            val result = repository.getScript("com.example.script")

            assertEquals("https://example.com/support", result?.manifest?.supportUrl)
        }

    @Test
    fun `getScript should set null supportUrl when no matching subscription`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            val scriptPackage = aScriptPackageDefinition("com.example.script")
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.script", user) } returns scriptPackage
            coEvery { subscriptionDataSource.getSubscriptions() } returns emptyList()

            val result = repository.getScript("com.example.script")

            assertNull(result?.manifest?.supportUrl)
        }

    @Test
    fun `getScript should return null when script is not found on filesystem`() =
        runTest {
            val user = mockk<User>(relaxed = true)
            coEvery { userSession.requireUser() } returns user
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.script", user) } returns null
            coEvery { subscriptionDataSource.getSubscriptions() } returns emptyList()

            val result = repository.getScript("com.example.script")

            assertNull(result)
        }

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
