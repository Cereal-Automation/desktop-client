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

class WindowsUpdateInstallerTest {
    @Test
    fun `runningAppPath returns the provided path when set`() {
        val installer = WindowsUpdateInstaller(appPathProvider = { "C:/Users/x/AppData/Local/Cereal/Cereal.exe" })

        assertEquals("C:/Users/x/AppData/Local/Cereal/Cereal.exe", installer.runningAppPath())
    }

    @Test
    fun `runningAppPath is null when the jpackage property is absent or blank`() {
        assertNull(WindowsUpdateInstaller(appPathProvider = { null }).runningAppPath())
        assertNull(WindowsUpdateInstaller(appPathProvider = { " " }).runningAppPath())
    }

    @Test
    fun `spawnUpdater launches the detached helper with the updater main and its arguments`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()

        val result = installer.spawnUpdater(fixture.installerFile, fixture.app, expectedSha256 = null)

        assertTrue(result)
        val command = fixture.launched.single()
        assertEquals(fixture.javaw.absolutePath, command[0])
        assertEquals("-cp", command[1])
        assertEquals("test-classpath.jar", command[2])
        assertEquals(WindowsUpdateInstaller.UPDATER_MAIN_CLASS, command[3])
        assertEquals(PID.toString(), command[4])
        assertEquals(fixture.installerFile.absolutePath, command[5])
        assertEquals(fixture.app.absolutePath, command[6])
        // No digest supplied ⇒ the trailing args round-trip back to a null expected digest, so the
        // helper on the other side skips verification rather than comparing against a literal "-".
        assertNull(UpdaterArguments.fromArgs(command.drop(4).toTypedArray())?.expectedSha256)
    }

    @Test
    fun `spawnUpdater forwards the expected digest to the helper`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()
        val sha = FileSha256.hash(fixture.installerFile)

        val result = installer.spawnUpdater(fixture.installerFile, fixture.app, sha)

        assertTrue(result)
        assertEquals(sha, fixture.launched.single()[7])
    }

    @Test
    fun `spawnUpdater throws and does not launch when the installer fails verification`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()
        val wrongSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        assertThrows<UpdateVerificationException> {
            installer.spawnUpdater(fixture.installerFile, fixture.app, wrongSha)
        }

        assertTrue(fixture.launched.isEmpty())
    }

    @Test
    fun `spawnUpdater returns false without launching when the install is not user-writable`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(writable = { false })

        val result = installer.spawnUpdater(fixture.installerFile, fixture.app, expectedSha256 = null)

        assertFalse(result)
        assertTrue(fixture.launched.isEmpty())
    }

    @Test
    fun `spawnUpdater returns false when the bundled javaw is missing`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp, createJavaw = false)
        val installer = fixture.installer()

        val result = installer.spawnUpdater(fixture.installerFile, fixture.app, expectedSha256 = null)

        assertFalse(result)
        assertTrue(fixture.launched.isEmpty())
    }

    private class Fixture(
        tmp: Path,
        createJavaw: Boolean = true,
    ) {
        private val installDir = File(tmp.toFile(), "Cereal").apply { mkdirs() }
        val app = File(installDir, "Cereal.exe").apply { writeText("APP") }
        val installerFile = File(tmp.toFile(), "cereal-client-latest.exe").apply { writeText("INSTALLER-BYTES") }
        val javaw =
            File(installDir, "runtime/bin/javaw.exe").apply {
                if (createJavaw) {
                    parentFile.mkdirs()
                    writeText("JAVAW")
                }
            }
        val launched = mutableListOf<List<String>>()

        fun installer(writable: (File) -> Boolean = { true }): WindowsUpdateInstaller =
            WindowsUpdateInstaller(
                appPathProvider = { app.absolutePath },
                currentPidProvider = { PID },
                javawResolver = { javaw },
                classpathProvider = { "test-classpath.jar" },
                writable = writable,
                helperLauncher = { command -> launched += command },
            )
    }

    private companion object {
        const val PID = 1234L
    }
}
