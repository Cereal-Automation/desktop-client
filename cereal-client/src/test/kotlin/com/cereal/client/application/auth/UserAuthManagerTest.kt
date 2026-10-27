package com.cereal.client.application.auth

import com.cereal.client.application.script.ScriptSyncManager
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemorySessionRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDiscordProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserAuthManagerTest {
    private val registeredUser =
        User(id = "new-user", name = "Jane Doe", email = "jane@example.com", encryptionKey = "key", accessToken = "token")

    @Test
    fun `register should sign the new account in`() =
        runTest {
            val sessionRepository = InMemorySessionRepository().apply { setSessionUser(null) }
            val scriptSyncManager = mockk<ScriptSyncManager> { every { syncWithProgress(any()) } returns emptyFlow() }
            val userAuthManager =
                UserAuthManager(
                    sessionRepository = sessionRepository,
                    authProvider = InMemoryAuthProvider(user = registeredUser),
                    discordRepository = InMemoryDiscordProvider(),
                    scriptSyncManager = scriptSyncManager,
                    scriptInstanceRepository = InMemoryScriptInstanceRepository(),
                    taskManager = mockk(relaxed = true),
                )

            userAuthManager.register(registeredUser.name, registeredUser.email, "s3cret")

            assertEquals(registeredUser, sessionRepository.getAuthenticatedUserFlow().first())
        }
}
