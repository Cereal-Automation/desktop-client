package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.domain.model.exception.UpdateVerificationException
import com.cereal.client.infrastructure.data.FileSha256
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AppImageInstallerTest {
    @Test
    fun `runningAppImagePath returns the provided path when set`() {
        val installer = AppImageInstaller(appImagePathProvider = { "/home/user/Cereal.AppImage" })

        assertEquals("/home/user/Cereal.AppImage", installer.runningAppImagePath())
    }

    @Test
    fun `runningAppImagePath is null when the env var is absent or blank`() {
        assertNull(AppImageInstaller(appImagePathProvider = { null }).runningAppImagePath())
        assertNull(AppImageInstaller(appImagePathProvider = { "  " }).runningAppImagePath())
    }

    @Test
    fun `replaceAndRelaunch overwrites the target with the download and launches it`(
        @TempDir tmp: Path,
    ) {
        val target = File(tmp.toFile(), "Cereal.AppImage").apply { writeText("OLD") }
        val downloaded = File(tmp.toFile(), "download.AppImage").apply { writeText("NEW") }
        val launched = mutableListOf<List<String>>()
        val installer =
            AppImageInstaller(
                appImagePathProvider = { target.absolutePath },
                processLauncher = { command -> launched += command },
            )

        val result = installer.replaceAndRelaunch(downloaded, target, expectedSha256 = null)

        assertTrue(result)
        assertEquals("NEW", target.readText())
        assertTrue(target.canExecute())
        // The new image is relaunched by absolute path.
        assertEquals(listOf(listOf(target.absolutePath)), launched)
        // The consumed download is cleaned up.
        assertFalse(downloaded.exists())
    }

    @Test
    fun `replaceAndRelaunch returns false and does not launch when the download is missing`(
        @TempDir tmp: Path,
    ) {
        val target = File(tmp.toFile(), "Cereal.AppImage").apply { writeText("OLD") }
        val missingDownload = File(tmp.toFile(), "does-not-exist.AppImage")
        val launched = mutableListOf<List<String>>()
        val installer =
            AppImageInstaller(
                appImagePathProvider = { target.absolutePath },
                processLauncher = { command -> launched += command },
            )

        val result = installer.replaceAndRelaunch(missingDownload, target, expectedSha256 = null)

        assertFalse(result)
        // Target is left intact and nothing is launched.
        assertEquals("OLD", target.readText())
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `replaceAndRelaunch installs and launches when the staged copy matches the expected sha`(
        @TempDir tmp: Path,
    ) {
        val target = File(tmp.toFile(), "Cereal.AppImage").apply { writeText("OLD") }
        val downloaded = File(tmp.toFile(), "download.AppImage").apply { writeText("NEW") }
        val launched = mutableListOf<List<String>>()
        val installer =
            AppImageInstaller(
                appImagePathProvider = { target.absolutePath },
                processLauncher = { command -> launched += command },
            )

        val result = installer.replaceAndRelaunch(downloaded, target, FileSha256.hash(downloaded))

        assertTrue(result)
        assertEquals("NEW", target.readText())
        assertEquals(listOf(listOf(target.absolutePath)), launched)
    }

    @Test
    fun `replaceAndRelaunch aborts without launching when the staged copy fails verification`(
        @TempDir tmp: Path,
    ) {
        val target = File(tmp.toFile(), "Cereal.AppImage").apply { writeText("OLD") }
        // The download no longer matches the digest from the release metadata — e.g. it was
        // swapped on disk after the streaming hash check (the CWE-367 window).
        val downloaded = File(tmp.toFile(), "download.AppImage").apply { writeText("TAMPERED") }
        val launched = mutableListOf<List<String>>()
        val installer =
            AppImageInstaller(
                appImagePathProvider = { target.absolutePath },
                processLauncher = { command -> launched += command },
            )
        val expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        assertThrows<UpdateVerificationException> {
            installer.replaceAndRelaunch(downloaded, target, expectedSha256)
        }

        // The running image is untouched, nothing is launched, and the staged copy is cleaned up.
        assertEquals("OLD", target.readText())
        assertTrue(launched.isEmpty())
        val stagedLeftovers = tmp.toFile().listFiles()!!.filter { it.name.endsWith(".new") }
        assertTrue(stagedLeftovers.isEmpty(), "staged copy should be deleted: $stagedLeftovers")
    }
}
