package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.InMemoryScriptPreferenceDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScriptPreferenceRepositoryImplTest {
    private val dataSource = InMemoryScriptPreferenceDataSource()

    // MockK: UserSession is a concrete final class; this stub only supplies the
    // authenticated user as a precondition (not call-sequence verification).
    private val userSession = mockk<UserSession>(relaxed = true)
    private lateinit var repository: ScriptPreferenceRepositoryImpl

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
            ScriptPreferenceRepositoryImpl(
                scriptPreferenceDataSource = dataSource,
                userSession = userSession,
            )
    }

    @Test
    fun `setString then getString observes the stored value`() =
        runTest {
            repository.setString("instance-1", "key-1", "value")

            assertEquals("value", repository.getString("instance-1", "key-1").first())
        }

    @Test
    fun `getString returns null before any value is set`() =
        runTest {
            assertNull(repository.getString("instance-1", "key-1").first())
        }

    @Test
    fun `deleteValue removes a previously stored string`() =
        runTest {
            repository.setString("instance-1", "key-1", "value")

            repository.deleteValue("instance-1", "key-1")

            assertNull(repository.getString("instance-1", "key-1").first())
        }

    @Test
    fun `setInt then getInt observes the stored value`() =
        runTest {
            repository.setInt("instance-1", "key-1", 42)

            assertEquals(42, repository.getInt("instance-1", "key-1").first())
        }

    @Test
    fun `deleteValue removes a previously stored int`() =
        runTest {
            repository.setInt("instance-1", "key-1", 42)

            repository.deleteValue("instance-1", "key-1")

            assertNull(repository.getInt("instance-1", "key-1").first())
        }

    @Test
    fun `setLong then getLong observes the stored value`() =
        runTest {
            repository.setLong("instance-1", "key-1", 99L)

            assertEquals(99L, repository.getLong("instance-1", "key-1").first())
        }

    @Test
    fun `deleteValue removes a previously stored long`() =
        runTest {
            repository.setLong("instance-1", "key-1", 99L)

            repository.deleteValue("instance-1", "key-1")

            assertNull(repository.getLong("instance-1", "key-1").first())
        }

    @Test
    fun `setFloat then getFloat observes the stored value`() =
        runTest {
            repository.setFloat("instance-1", "key-1", 1.5f)

            assertEquals(1.5f, repository.getFloat("instance-1", "key-1").first())
        }

    @Test
    fun `deleteValue removes a previously stored float`() =
        runTest {
            repository.setFloat("instance-1", "key-1", 1.5f)

            repository.deleteValue("instance-1", "key-1")

            assertNull(repository.getFloat("instance-1", "key-1").first())
        }

    @Test
    fun `setBoolean then getBoolean observes the stored value`() =
        runTest {
            repository.setBoolean("instance-1", "key-1", true)

            assertEquals(true, repository.getBoolean("instance-1", "key-1").first())
        }

    @Test
    fun `deleteValue removes a previously stored boolean`() =
        runTest {
            repository.setBoolean("instance-1", "key-1", true)

            repository.deleteValue("instance-1", "key-1")

            assertNull(repository.getBoolean("instance-1", "key-1").first())
        }
}
