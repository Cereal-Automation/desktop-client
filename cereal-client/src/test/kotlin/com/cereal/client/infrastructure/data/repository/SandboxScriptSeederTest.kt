package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SandboxScriptSeederTest {
    private val user =
        User(
            id = "test-user",
            name = "Cereal test user",
            email = "noreply@cereal-automation.com",
            encryptionKey = "",
            accessToken = "test-token",
            isGuest = true,
        )

    @Test
    fun seedsBundledSampleScriptIntoUserDirectory(
        @TempDir tempDir: File,
    ) {
        val config = InMemoryApplicationConfig(baseDir = tempDir)

        SandboxScriptSeeder(config).seedFor(user)

        val seeded = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar")
        assertTrue(seeded.exists(), "expected bundled release.jar to be seeded at ${seeded.absolutePath}")
    }

    @Test
    fun isIdempotentAndDoesNotOverwriteExistingScript(
        @TempDir tempDir: File,
    ) {
        val config = InMemoryApplicationConfig(baseDir = tempDir)
        val seeder = SandboxScriptSeeder(config)
        val seeded = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar")

        seeder.seedFor(user)
        // Mark the file so we can prove a second seed leaves the developer's copy untouched.
        seeded.writeText("local edit")

        seeder.seedFor(user)

        assertEquals("local edit", seeded.readText())
    }

    @Test
    fun reseedsStaleScriptThatHasNoProvenanceMarker(
        @TempDir tempDir: File,
    ) {
        val config = InMemoryApplicationConfig(baseDir = tempDir)
        val seeded = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar")

        // Simulate a JAR seeded before provenance tracking existed: stale content, no .seedhash
        // sidecar (e.g. a release.jar predating the manifest's sdk_version).
        seeded.parentFile.mkdirs()
        seeded.writeText("stale jar predating sdk_version")

        SandboxScriptSeeder(config).seedFor(user)

        val bundled =
            javaClass.getResourceAsStream("/sandbox-scripts/release.jar")!!.use { it.readBytes() }
        assertContentEquals(
            bundled,
            seeded.readBytes(),
            "expected the stale seeded release.jar to be refreshed from the bundle",
        )
    }

    @Test
    fun doesNotRewriteSeededScriptWhenAlreadyUpToDate(
        @TempDir tempDir: File,
    ) {
        val config = InMemoryApplicationConfig(baseDir = tempDir)
        val seeder = SandboxScriptSeeder(config)
        val seeded = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar")

        seeder.seedFor(user)
        // Pin a known modification time; an up-to-date file must be left untouched on re-seed.
        val pinnedTime = 1_000_000_000L
        assertTrue(seeded.setLastModified(pinnedTime))

        seeder.seedFor(user)

        assertEquals(pinnedTime, seeded.lastModified(), "expected an up-to-date seed to be skipped")
    }

    @Test
    fun reseedsUntouchedScriptWhenBundleChanged(
        @TempDir tempDir: File,
    ) {
        val config = InMemoryApplicationConfig(baseDir = tempDir)
        val seeded = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar")
        val marker = File(config.getScriptsDirectory, "${user.id}${File.separator}release.jar.seedhash")

        // Simulate a JAR that was seeded from an older bundle and never edited since: the marker
        // records exactly the current (old) content's hash, but the bundle has since changed.
        seeded.parentFile.mkdirs()
        val oldContent = "old bundle content".toByteArray()
        seeded.writeBytes(oldContent)
        marker.writeText(sha256(oldContent))

        SandboxScriptSeeder(config).seedFor(user)

        val bundled =
            javaClass.getResourceAsStream("/sandbox-scripts/release.jar")!!.use { it.readBytes() }
        assertContentEquals(
            bundled,
            seeded.readBytes(),
            "expected an untouched seed to be refreshed when the bundle changed",
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
