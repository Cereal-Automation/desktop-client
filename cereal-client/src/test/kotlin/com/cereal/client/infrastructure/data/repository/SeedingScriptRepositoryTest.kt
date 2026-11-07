package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SeedingScriptRepositoryTest {
    private val delegate = mockk<ScriptRepository>(relaxed = true)
    private val userSession = mockk<UserSession>(relaxed = true)
    private val sandboxScriptSeeder = mockk<SandboxScriptSeeder>(relaxed = true)
    private val repository = SeedingScriptRepository(delegate, userSession, sandboxScriptSeeder)

    private val user =
        User(id = "user-1", name = "Test User", email = "test@example.com", encryptionKey = "key", accessToken = "token")

    @Test
    fun `getInstalledScripts seeds before delegating`() =
        runTest {
            coEvery { userSession.requireUser() } returns user

            repository.getInstalledScripts()

            coVerifyOrder {
                sandboxScriptSeeder.seedFor(user)
                delegate.getInstalledScripts()
            }
        }

    @Test
    fun `getScript seeds before delegating`() =
        runTest {
            coEvery { userSession.requireUser() } returns user

            repository.getScript("com.example.script")

            coVerifyOrder {
                sandboxScriptSeeder.seedFor(user)
                delegate.getScript("com.example.script")
            }
        }

    @Test
    fun `removeScript delegates without seeding`() =
        runTest {
            repository.removeScript(mockk())

            verify(exactly = 0) { sandboxScriptSeeder.seedFor(any()) }
        }
}
