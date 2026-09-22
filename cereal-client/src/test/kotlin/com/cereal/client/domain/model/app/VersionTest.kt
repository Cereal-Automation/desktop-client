package com.cereal.client.domain.model.app

import com.cereal.client.domain.model.exception.InvalidVersionException
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VersionTest {
    private fun version(
        downloadUrl: String = "https://downloads.test/app.dmg",
        downloadSha256: String? = null,
    ) = Version(
        version = SemVer(2, 0, 0),
        minRequiredVersion = SemVer(1, 0, 0),
        downloadUrl = downloadUrl,
        downloadSha256 = downloadSha256,
    )

    @Test
    fun `should create valid version without sha256`() {
        assertNotNull(version())
    }

    @Test
    fun `should create valid version with 64-char hex sha256`() {
        assertNotNull(version(downloadSha256 = "a".repeat(64)))
    }

    @Test
    fun `should allow blank downloadUrl for store builds and download-less metadata`() {
        // A blank download URL is valid: store builds update via their store URL, and a plain
        // version check needs no installer at all. Requiring one here crashed the app (the
        // metadata legitimately carries a blank download_url); presence is enforced at download.
        assertNotNull(version(downloadUrl = " "))
    }

    @Test
    fun `should fail when sha256 is not 64 characters`() {
        val exception = assertThrows<InvalidVersionException> { version(downloadSha256 = "abc123") }
        assertEquals("DownloadSha256 must be a 64-character hex SHA-256", exception.message)
    }

    @Test
    fun `should fail when sha256 contains non-hex characters`() {
        val exception = assertThrows<InvalidVersionException> { version(downloadSha256 = "z".repeat(64)) }
        assertEquals("DownloadSha256 must be a 64-character hex SHA-256", exception.message)
    }
}
