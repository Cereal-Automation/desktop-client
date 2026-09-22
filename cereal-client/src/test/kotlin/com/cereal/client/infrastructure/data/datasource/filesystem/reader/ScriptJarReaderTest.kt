package com.cereal.client.infrastructure.data.datasource.filesystem.reader

import com.cereal.client.application.Environment
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal_automation.cereal_client.BuildConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScriptJarReaderTest {
    @TempDir
    lateinit var tempFolder: Path

    private val testEncryptionKey = Encryption.getEncryptionKey("test-encryption-key", "32-chars-lon")

    @BeforeEach
    fun setUp() {
        // Mock BuildConfig to force encryption during tests
        mockkObject(BuildConfig)
        every { BuildConfig.ENVIRONMENT } returns Environment.PRODUCTION
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BuildConfig)
    }

    @Test
    fun `readManifest should parse valid manifest json correctly with encryption`() {
        val manifestJson =
            """
            {
                "package_name": "com.example.test",
                "name": "Test Script",
                "version_code": 123,
                "script": "com.example.TestScript",
                "child_scripts": ["com.example.ChildScript1", "com.example.ChildScript2"]
            }
            """.trimIndent()

        val jarFile = createJarWithManifest(manifestJson)
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNotNull(reader.manifest)
        assertEquals("com.example.test", reader.manifest!!.packageName)
        assertEquals("Test Script", reader.manifest!!.name)
        assertEquals(123L, reader.manifest!!.versionCode)
    }

    @Test
    fun `readManifest should parse minimal manifest json correctly with encryption`() {
        val manifestJson =
            """
            {
                "package_name": "com.example.minimal",
                "name": "Minimal Script",
                "version_code": 1,
                "script": "com.example.TestScript"
            }
            """.trimIndent()

        val jarFile = createJarWithManifest(manifestJson)
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNotNull(reader.manifest)
        assertEquals("com.example.minimal", reader.manifest!!.packageName)
        assertEquals("Minimal Script", reader.manifest!!.name)
        assertEquals(1L, reader.manifest!!.versionCode)
    }

    @Test
    fun `readManifest should return null for invalid json with encryption`() {
        val invalidManifestJson =
            """
            {
                "package_name": "com.example.test",
                "name": "Test Script",
                "invalid_json"
            }
            """.trimIndent()

        val jarFile = createJarWithManifest(invalidManifestJson)
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNull(reader.manifest)
    }

    @Test
    fun `readManifest should return null when manifest file is missing with encryption`() {
        val jarFile = createJarWithoutManifest()
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNull(reader.manifest)
    }

    @Test
    fun `readManifest should return null for empty manifest file with encryption`() {
        val jarFile = createJarWithManifest("")
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNull(reader.manifest)
    }

    @Test
    fun `readManifest should parse manifest with instructions correctly with encryption`() {
        val manifestJson =
            """
            {
                "package_name": "com.example.test",
                "name": "Test Script",
                "version_code": 123,
                "script": "com.example.TestScript",
                "instructions": "Please configure all settings before running this script."
            }
            """.trimIndent()

        val jarFile = createJarWithManifest(manifestJson)
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNotNull(reader.manifest)
        assertEquals("com.example.test", reader.manifest!!.packageName)
        assertEquals("Test Script", reader.manifest!!.name)
        assertEquals(123L, reader.manifest!!.versionCode)
        assertEquals("Please configure all settings before running this script.", reader.manifest!!.instructions)
    }

    @Test
    fun `readManifest should handle null instructions correctly with encryption`() {
        val manifestJson =
            """
            {
                "package_name": "com.example.test",
                "name": "Test Script",
                "version_code": 123,
                "script": "com.example.TestScript"
            }
            """.trimIndent()

        val jarFile = createJarWithManifest(manifestJson)
        val encryption = Encryption()
        val reader = ScriptJarReader(jarFile, testEncryptionKey, encryption)

        assertNotNull(reader.manifest)
        assertNull(reader.manifest!!.instructions)
    }

    private fun createJarWithManifest(manifestContent: String): File {
        val jarFile = tempFolder.resolve("test-script.jar").toFile()
        val byteArrayOutputStream = ByteArrayOutputStream()

        // Create jar with manifest
        JarOutputStream(byteArrayOutputStream).use { jarOut ->
            val manifestEntry = JarEntry("manifest.json")
            jarOut.putNextEntry(manifestEntry)
            jarOut.write(manifestContent.toByteArray())
            jarOut.closeEntry()
        }

        // Write jar with encrypted entries to file (will be encrypted since we mocked BuildConfig.ENVIRONMENT)
        val encryption = Encryption()
        encryption.writeJar(
            inputStream = byteArrayOutputStream.toByteArray().inputStream(),
            outputStream = jarFile.outputStream(),
            key = testEncryptionKey,
        )

        return jarFile
    }

    private fun createJarWithoutManifest(): File {
        val jarFile = tempFolder.resolve("test-script-no-manifest.jar").toFile()
        val byteArrayOutputStream = ByteArrayOutputStream()

        // Create empty jar
        JarOutputStream(byteArrayOutputStream).use { jarOut ->
            val dummyEntry = JarEntry("dummy.txt")
            jarOut.putNextEntry(dummyEntry)
            jarOut.write("dummy content".toByteArray())
            jarOut.closeEntry()
        }

        // Write jar with encrypted entries to file (will be encrypted since we mocked BuildConfig.ENVIRONMENT)
        val encryption = Encryption()
        encryption.writeJar(
            inputStream = byteArrayOutputStream.toByteArray().inputStream(),
            outputStream = jarFile.outputStream(),
            key = testEncryptionKey,
        )

        return jarFile
    }
}
