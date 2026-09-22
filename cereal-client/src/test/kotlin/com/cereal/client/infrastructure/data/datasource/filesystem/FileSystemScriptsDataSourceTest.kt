package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.exception.LoadScriptException
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FileSystemScriptsDataSourceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockConfig: ApplicationConfig
    private lateinit var mockScriptConfigurationDefinitionBuilder: ScriptConfigurationDefinitionBuilder
    private lateinit var dataSource: FileSystemScriptsDataSource
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        mockConfig = mockk()
        mockScriptConfigurationDefinitionBuilder = mockk()

        testUser =
            User(
                id = "test-user-id",
                name = "Test User",
                email = "test@example.com",
                encryptionKey = "test-encryption-key",
                accessToken = "fake-access-token-for-testing",
            )

        every { mockConfig.getScriptsDirectory } returns tempDir.toFile()
        every { mockConfig.databaseEncryptionKey } returns "app-encryption-key"
        every { mockConfig.fileEncryptionKey } returns "file-encryption-key"

        val encryption = Encryption()
        dataSource = FileSystemScriptsDataSource(mockConfig, mockScriptConfigurationDefinitionBuilder, encryption)

        // Ensure user script directory exists
        val userDir = File(tempDir.toFile(), testUser.id)
        userDir.mkdirs()
    }

    @Test
    fun `getScriptDefinitions should return empty flow for new user`() =
        runTest {
            // When
            val result = dataSource.getScriptDefinitions(testUser).first()

            // Then
            assertTrue(result.isEmpty())

            // Verify user directory was created
            val userDir = File(tempDir.toFile(), testUser.id)
            assertTrue(userDir.exists())
            assertTrue(userDir.isDirectory)
        }

    @Test
    fun `getScriptPackageDefinition should return null when package not found`() =
        runTest {
            // When
            val result = dataSource.getScriptPackageDefinition("non.existent.package", testUser)

            // Then
            assertNull(result)
        }

    @Test
    fun `multiple users should have separate script flows`() =
        runTest {
            // Given
            val user1 = testUser
            val user2 = User("user2", "User 2", "user2@example.com", "key2", "fake-token-2")

            // When
            val flow1 = dataSource.getScriptDefinitions(user1)
            val flow2 = dataSource.getScriptDefinitions(user2)

            // Then
            assertNotSame(flow1, flow2)

            // Verify separate directories were created
            val userDir1 = File(tempDir.toFile(), user1.id)
            val userDir2 = File(tempDir.toFile(), user2.id)
            assertTrue(userDir1.exists())
            assertTrue(userDir2.exists())
            assertNotEquals(userDir1.absolutePath, userDir2.absolutePath)
        }

    @Test
    fun `getOrCreateScriptsFlow should reuse existing flow for same user`() =
        runTest {
            // Given
            val flow1 = dataSource.getScriptDefinitions(testUser)

            // When
            val flow2 = dataSource.getScriptDefinitions(testUser)

            // Then
            assertSame(flow1, flow2) // Should be the same flow instance
        }

    @Test
    fun `getUserScriptDirectory should return correct path format`() =
        runTest {
            // Given
            val expectedPath = "${tempDir.toFile().absolutePath}${File.separator}${testUser.id}${File.separator}"

            // When - Trigger directory creation by getting script definitions
            dataSource.getScriptDefinitions(testUser).first()

            // Then
            val userDir = File(tempDir.toFile(), testUser.id)
            assertTrue(userDir.exists())
            assertTrue(userDir.isDirectory)
            assertEquals(expectedPath, "${userDir.absolutePath}${File.separator}")
        }

    @Test
    fun `initializeScripts should ignore non-jar files`() =
        runTest {
            // Given
            val userDir = File(tempDir.toFile(), testUser.id)
            userDir.mkdirs()

            // Create non-jar files
            File(userDir, "text-file.txt").createNewFile()
            File(userDir, "image-file.png").createNewFile()
            File(userDir, "config-file.json").createNewFile()

            // When
            val result = dataSource.getScriptDefinitions(testUser).first()

            // Then
            assertTrue(result.isEmpty()) // Non-jar files should be ignored
        }

    @Test
    fun `deleteScript should remove file and update flow`() =
        runTest {
            // Given
            val scriptFile = File(tempDir.toFile(), "${testUser.id}/test-script.jar")
            scriptFile.parentFile.mkdirs()
            scriptFile.createNewFile()

            val mockScriptPackage = mockk<com.cereal.client.domain.model.script.ScriptPackage>()
            every { mockScriptPackage.source } returns scriptFile
            every { mockScriptPackage.manifest.packageName } returns "com.test.script"

            // First add the script to the flow by getting definitions
            dataSource.getScriptDefinitions(testUser).first()

            // When
            dataSource.deleteScript(mockScriptPackage, testUser)

            // Then
            assertFalse(scriptFile.exists())

            // Verify script is removed from flow
            val definitions = dataSource.getScriptDefinitions(testUser).first()
            assertFalse(definitions.any { it.manifest.packageName == "com.test.script" })
        }

    @Test
    fun `initializeScripts should handle empty directory`() =
        runTest {
            // Given
            val userDir = File(tempDir.toFile(), testUser.id)
            userDir.mkdirs()

            // When
            val result = dataSource.getScriptDefinitions(testUser).first()

            // Then
            assertTrue(result.isEmpty())
            assertTrue(userDir.exists())
            assertTrue(userDir.isDirectory)
        }

    @Test
    fun `initializeScripts should create directory if it does not exist`() =
        runTest {
            // Given - Use a different user to avoid conflict with setUp
            val differentUser =
                User("different-user-id", "Different User", "different@example.com", "diff-key", "fake-token-diff")
            val userDir = File(tempDir.toFile(), differentUser.id)
            assertFalse(userDir.exists())

            // When
            val result = dataSource.getScriptDefinitions(differentUser).first()

            // Then
            assertTrue(result.isEmpty())
            assertTrue(userDir.exists())
            assertTrue(userDir.isDirectory)
        }

    @Test
    fun `flow state should be maintained across multiple calls`() =
        runTest {
            // Given
            val flow = dataSource.getScriptDefinitions(testUser)

            // When
            val result1 = flow.first()
            val result2 = flow.first()

            // Then
            assertEquals(result1.size, result2.size)
            assertTrue(result1.isEmpty())
            assertTrue(result2.isEmpty())
        }

    @Test
    fun `different users should have independent flows`() =
        runTest {
            // Given
            val user1 = User("user1", "User 1", "user1@example.com", "key1", "fake-token-1")
            val user2 = User("user2", "User 2", "user2@example.com", "key2", "fake-token-2")

            // When
            val flow1 = dataSource.getScriptDefinitions(user1)
            val flow2 = dataSource.getScriptDefinitions(user2)

            val result1 = flow1.first()
            val result2 = flow2.first()

            // Then
            assertNotSame(flow1, flow2)
            assertEquals(result1.size, result2.size)
            assertTrue(result1.isEmpty())
            assertTrue(result2.isEmpty())

            // Verify separate directories
            val userDir1 = File(tempDir.toFile(), user1.id)
            val userDir2 = File(tempDir.toFile(), user2.id)
            assertTrue(userDir1.exists())
            assertTrue(userDir2.exists())
            assertNotEquals(userDir1, userDir2)
        }

    @Test
    fun `initializeScripts should delete corrupted JAR files`() =
        runTest {
            // Given
            val userDir = File(tempDir.toFile(), testUser.id)
            userDir.mkdirs()

            // Create a corrupted JAR file (invalid content)
            val corruptedJar = File(userDir, "corrupted-script.jar")
            corruptedJar.writeText("This is not a valid JAR file content")

            assertTrue(corruptedJar.exists())

            // When
            val result = dataSource.getScriptDefinitions(testUser).first()

            // Then
            assertTrue(result.isEmpty()) // No scripts should be loaded
            assertFalse(corruptedJar.exists()) // Corrupted JAR should be deleted
        }

    @Test
    fun `initializeScripts should delete multiple corrupted JAR files`() =
        runTest {
            // Given
            val userDir = File(tempDir.toFile(), testUser.id)
            userDir.mkdirs()

            // Create multiple corrupted JAR files
            val corruptedJar1 = File(userDir, "corrupted-script-1.jar")
            val corruptedJar2 = File(userDir, "corrupted-script-2.jar")
            val corruptedJar3 = File(userDir, "corrupted-script-3.jar")

            corruptedJar1.writeText("Invalid JAR 1")
            corruptedJar2.writeText("Invalid JAR 2")
            corruptedJar3.writeText("Invalid JAR 3")

            assertTrue(corruptedJar1.exists())
            assertTrue(corruptedJar2.exists())
            assertTrue(corruptedJar3.exists())

            // When
            val result = dataSource.getScriptDefinitions(testUser).first()

            // Then
            assertTrue(result.isEmpty())
            assertFalse(corruptedJar1.exists())
            assertFalse(corruptedJar2.exists())
            assertFalse(corruptedJar3.exists())
        }

    @Test
    fun `storeScript should delete JAR file when it fails to load`() =
        runTest {
            // Given
            val mockRelease = mockk<Release>()
            every { mockRelease.versionName } returns "1.0.0"
            every { mockRelease.versionCode } returns 1

            val packageName = "com.test.invalid"
            val corruptedContent = "This is not a valid JAR file"
            val inputStream = corruptedContent.byteInputStream()

            // When & Then
            val exception =
                assertThrows<LoadScriptException> {
                    dataSource.storeScript(packageName, mockRelease, inputStream, testUser, SemVer(2, 0, 0))
                }

            // Verify the exception is thrown
            assertEquals(packageName, exception.packageName)

            // Verify the file was deleted after failed load attempt
            val userDir = File(tempDir.toFile(), testUser.id)
            val expectedFile = File(userDir, "$packageName-${mockRelease.versionName}-${mockRelease.versionCode}.jar")
            assertFalse(expectedFile.exists()) // File should be deleted
        }

    @Test
    fun `storeScript should reject packageName that escapes the user directory`() =
        runTest {
            // Given - a packageName containing path separators that would escape the user script dir
            val mockRelease = mockk<Release>()
            every { mockRelease.versionName } returns "1.0.0"
            every { mockRelease.versionCode } returns 1

            val maliciousPackageName = "..${File.separator}escaped"
            val inputStream = "valid-looking content".byteInputStream()

            // When & Then
            assertThrows<LoadScriptException> {
                dataSource.storeScript(maliciousPackageName, mockRelease, inputStream, testUser, SemVer(2, 0, 0))
            }

            // Nothing should be written outside (or inside) the user directory
            val escaped = File(tempDir.toFile(), "escaped-1.0.0-1.jar")
            assertFalse(escaped.exists())
        }

    @Test
    fun `storeScript should reject packageName with disallowed characters`() =
        runTest {
            val mockRelease = mockk<Release>()
            every { mockRelease.versionName } returns "1.0.0"
            every { mockRelease.versionCode } returns 1

            assertThrows<LoadScriptException> {
                dataSource.storeScript("com/test/evil", mockRelease, "x".byteInputStream(), testUser, SemVer(2, 0, 0))
            }
        }

    @Test
    fun `storeScript should reject Windows reserved packageName`() =
        runTest {
            val mockRelease = mockk<Release>()
            every { mockRelease.versionName } returns "1.0.0"
            every { mockRelease.versionCode } returns 1

            assertThrows<LoadScriptException> {
                dataSource.storeScript("CON", mockRelease, "x".byteInputStream(), testUser, SemVer(2, 0, 0))
            }
        }

    @Test
    fun `storeScript should reject malicious versionName`() =
        runTest {
            val mockRelease = mockk<Release>()
            every { mockRelease.versionName } returns "..${File.separator}.."
            every { mockRelease.versionCode } returns 1

            assertThrows<LoadScriptException> {
                dataSource.storeScript("com.test.valid", mockRelease, "x".byteInputStream(), testUser, SemVer(2, 0, 0))
            }
        }
}
