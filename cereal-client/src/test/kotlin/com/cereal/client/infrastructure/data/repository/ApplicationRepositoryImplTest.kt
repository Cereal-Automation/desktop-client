package com.cereal.client.infrastructure.data.repository

import com.cereal.client.fixtures.InMemoryKeyValueDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationRepositoryImplTest {
    private val keyValueDataSource = InMemoryKeyValueDataSource()
    private lateinit var repository: ApplicationRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = ApplicationRepositoryImpl(keyValueDataSource)
    }

    @Test
    fun `getInstalledVersion returns a parsed SemVer`() =
        runTest {
            val version = repository.getInstalledVersion()

            assertTrue(version.major >= 0)
        }

    @Test
    fun `getSdkVersion returns a parsed SemVer`() =
        runTest {
            val version = repository.getSdkVersion()

            assertTrue(version.major >= 0)
        }

    @Test
    fun `getVersionCheckTime returns null before any timestamp is stored`() =
        runTest {
            assertNull(repository.getVersionCheckTime())
        }

    @Test
    fun `setVersionCheckTime then getVersionCheckTime round-trips the timestamp`() =
        runTest {
            val timestamp = 1_700_000_000_000L

            repository.setVersionCheckTime(timestamp)

            assertEquals(timestamp, repository.getVersionCheckTime())
        }
}
