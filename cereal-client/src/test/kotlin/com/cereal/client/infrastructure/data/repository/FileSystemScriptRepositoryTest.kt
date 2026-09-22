package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSystemScriptRepositoryTest {
    private val fileSystemScriptsDataSource = mockk<FileSystemScriptsDataSource>(relaxed = true)
    private val userSession = mockk<UserSession>(relaxed = true)
    private val sandboxScriptSeeder = mockk<SandboxScriptSeeder>(relaxed = true)
    private lateinit var repository: FileSystemScriptRepository

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "key",
            accessToken = "token",
        )

    @BeforeEach
    fun setUp() {
        coEvery { userSession.requireUser() } returns user
        repository =
            FileSystemScriptRepository(
                fileSystemScriptsDataSource = fileSystemScriptsDataSource,
                userSession = userSession,
                sandboxScriptSeeder = sandboxScriptSeeder,
            )
    }

    @Test
    fun `getInstalledScripts seeds scripts and maps definitions to script packages`() =
        runTest {
            val definition = aScriptPackageDefinition("com.example.script")
            every { fileSystemScriptsDataSource.getScriptDefinitions(user) } returns flowOf(listOf(definition))

            val result = repository.getInstalledScripts().first()

            verify(exactly = 1) { sandboxScriptSeeder.seedFor(user) }
            assertEquals(1, result.size)
            assertEquals("com.example.script", result[0].manifest.packageName)
            assertNull(result[0].manifest.supportUrl)
        }

    @Test
    fun `getScript seeds scripts and returns mapped package when found`() =
        runTest {
            val definition = aScriptPackageDefinition("com.example.script")
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.script", user) } returns definition

            val result = repository.getScript("com.example.script")

            verify(exactly = 1) { sandboxScriptSeeder.seedFor(user) }
            assertEquals("com.example.script", result?.manifest?.packageName)
            assertNull(result?.manifest?.supportUrl)
        }

    @Test
    fun `getScript returns null when definition not found`() =
        runTest {
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.missing", user) } returns null

            val result = repository.getScript("com.example.missing")

            assertNull(result)
        }

    @Test
    fun `removeScript delegates deletion to data source with current user`() =
        runTest {
            val scriptPackage = aScriptPackage("com.example.script")

            repository.removeScript(scriptPackage)

            verify(exactly = 1) { fileSystemScriptsDataSource.deleteScript(scriptPackage, user) }
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
}
