package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import com.cereal.client.infrastructure.data.repository.fixtures.FakeKeyValueDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationPreferenceRepositoryImplTest {
    private lateinit var keyValueDataSource: FakeKeyValueDataSource
    private lateinit var userSession: UserSession
    private lateinit var repository: ApplicationPreferenceRepositoryImpl

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "encryption-key",
            accessToken = "access-token",
        )

    @BeforeEach
    fun setUp() {
        keyValueDataSource = FakeKeyValueDataSource()
        userSession = mockk(relaxed = true)
        coEvery { userSession.requireUser() } returns user
        repository = ApplicationPreferenceRepositoryImpl(keyValueDataSource, userSession)
    }

    @Test
    fun `discord activity status round trips`() =
        runTest {
            repository.setDiscordActivityStatusEnabled(false)
            assertEquals(false, repository.isDiscordActivityStatusEnabled().first())

            repository.setDiscordActivityStatusEnabled(true)
            assertEquals(true, repository.isDiscordActivityStatusEnabled().first())
        }

    @Test
    fun `discord activity status returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.DiscordActivityStatusEnabled.defaultValue,
                repository.isDiscordActivityStatusEnabled().first(),
            )
        }

    @Test
    fun `development scripts round trips`() =
        runTest {
            repository.setDevelopmentScriptsEnabled(true)
            assertEquals(true, repository.isDevelopmentScriptsEnabled().first())
        }

    @Test
    fun `development scripts returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.DevelopmentScriptsEnabled.defaultValue,
                repository.isDevelopmentScriptsEnabled().first(),
            )
        }

    @Test
    fun `show debug logs round trips`() =
        runTest {
            repository.setShowDebugLogs(true)
            assertEquals(true, repository.isShowDebugLogs().first())
        }

    @Test
    fun `show debug logs returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.ShowDebugLogs.defaultValue,
                repository.isShowDebugLogs().first(),
            )
        }

    @Test
    fun `proxy health check interval round trips`() =
        runTest {
            repository.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_6_HOURS)
            assertEquals(
                ProxyHealthCheckInterval.EVERY_6_HOURS,
                repository.getProxyHealthCheckInterval().first(),
            )

            repository.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_24_HOURS)
            assertEquals(
                ProxyHealthCheckInterval.EVERY_24_HOURS,
                repository.getProxyHealthCheckInterval().first(),
            )
        }

    @Test
    fun `proxy health check interval returns default when empty`() =
        runTest {
            assertEquals(
                ProxyHealthCheckInterval.Default,
                repository.getProxyHealthCheckInterval().first(),
            )
        }
}
