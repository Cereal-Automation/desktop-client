package com.cereal.client.infrastructure.data.datasource.database.integration

import androidx.room.useReaderConnection
import com.cereal.client.application.Environment
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.room.RoomKeyValueDataSource
import com.cereal.client.infrastructure.data.repository.LlmProviderConfigRepositoryImpl
import com.cereal_automation.cereal_client.BuildConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LlmProviderConfigRepositoryImpl] against a real user Room database: the config persists in the
 * user's settings store with the API key encrypted at rest, and clearing leaves nothing behind.
 */
class LlmProviderConfigRepositoryIntegrationTest : RoomUserDatabaseTestBase() {
    // The test build runs as LOCAL, which stores EncryptedString values in plaintext; force the
    // production path so the at-rest assertion means something.
    @BeforeEach
    fun forceEncryptedStorage() {
        mockkObject(BuildConfig)
        every { BuildConfig.ENVIRONMENT } returns Environment.PRODUCTION
    }

    @AfterEach
    fun restoreEnvironment() {
        unmockkObject(BuildConfig)
    }

    private fun newRepository(): LlmProviderConfigRepositoryImpl {
        val userSession = mockk<UserSession> { coEvery { requireUser() } returns testUser }
        return LlmProviderConfigRepositoryImpl(RoomKeyValueDataSource(roomDatabases), userSession)
    }

    private val anthropicConfig =
        LlmProviderConfig.of(
            provider = LlmProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            apiKey = "sk-ant-plaintext-key",
            model = "claude-opus-5-5",
        )

    private suspend fun storedSettingValues(): List<String> =
        roomDatabases.getUserDatabase(testUser).useReaderConnection { connection ->
            connection.usePrepared("SELECT value FROM application_settings") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    @Test
    fun `get returns null when nothing is saved`() =
        runTest {
            assertNull(newRepository().get())
        }

    @Test
    fun `saved config round-trips through a fresh repository`() =
        runTest {
            newRepository().save(anthropicConfig)

            assertEquals(anthropicConfig, newRepository().get())
        }

    @Test
    fun `config without a key round-trips`() =
        runTest {
            val local = LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "llama3")

            newRepository().save(local)

            assertEquals(local, newRepository().get())
        }

    @Test
    fun `api key is encrypted at rest`() =
        runTest {
            newRepository().save(anthropicConfig)

            val stored = storedSettingValues()
            assertTrue(stored.isNotEmpty())
            assertTrue(stored.none { "sk-ant-plaintext-key" in it }, "API key must not be stored in plaintext")
        }

    @Test
    fun `clear removes every field`() =
        runTest {
            val repository = newRepository()
            repository.save(anthropicConfig)

            repository.clear()

            assertNull(newRepository().get())
            assertEquals(emptyList(), storedSettingValues())
        }
}
