package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Exercises the real encryption + filesystem edge: bytes written by [FileSystemArtifactDataSource] are stored
 * encrypted and round-trip back to the original on read.
 */
class FileSystemArtifactDataSourceTest {
    private lateinit var tempDir: File
    private lateinit var dataSource: FileSystemArtifactDataSource

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "12345678901234567890123456789012",
            accessToken = "token",
        )

    @BeforeEach
    fun setUp() {
        tempDir =
            File.createTempFile("artifacts-test", "").apply {
                delete()
                mkdirs()
            }
        val config =
            mockk<ApplicationConfig> {
                every { databaseDirectory } returns tempDir
                every { fileEncryptionKey } returns "app-key-app-key-app-key-app-key1"
            }
        dataSource = FileSystemArtifactDataSource(config)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `write then copyToFile round-trips identical bytes`() =
        runTest {
            val bytes = "sku,price\nABC-1,19.99\n".toByteArray()

            dataSource.write(user, "task-1/artifact-1", bytes)
            val destination = File(tempDir, "download.csv")
            dataSource.copyToFile(user, "task-1/artifact-1", destination)

            assertArrayEquals(bytes, destination.readBytes())
        }

    @Test
    fun `stored bytes are encrypted at rest`() =
        runTest {
            val bytes = "plaintext-secret".toByteArray()

            dataSource.write(user, "task-1/artifact-1", bytes)

            val storedFile = File(File(File(tempDir, "Artifacts"), user.id), "task-1/artifact-1")
            assertTrue(storedFile.exists())
            assertFalse(storedFile.readBytes().contentEquals(bytes), "Bytes on disk must not be plaintext")
        }

    @Test
    fun `deleteForTask removes the task directory`() =
        runTest {
            dataSource.write(user, "task-1/artifact-1", "a".toByteArray())
            dataSource.write(user, "task-1/artifact-2", "b".toByteArray())

            dataSource.deleteForTask(user, "task-1")

            val taskDir = File(File(File(tempDir, "Artifacts"), user.id), "task-1")
            assertFalse(taskDir.exists())
        }
}
