package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.infrastructure.data.FileSha256
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class UpdateApplierTest {
    @Test
    fun `apply waits for the parent, verifies, installs and relaunches on the happy path`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val applier = fixture.applier()

        val result =
            applier.apply(UpdaterArguments(PARENT_PID, fixture.installer, fixture.app, FileSha256.hash(fixture.installer)))

        assertTrue(result)
        // The parent exit is awaited before the installer runs (that is what releases the file locks).
        assertEquals(listOf("await:$PARENT_PID", "install", "relaunch"), fixture.events)
        assertEquals(listOf(fixture.app), fixture.relaunched)
    }

    @Test
    fun `apply runs without verification when no digest is supplied`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val applier = fixture.applier()

        val result = applier.apply(UpdaterArguments(PARENT_PID, fixture.installer, fixture.app, expectedSha256 = null))

        assertTrue(result)
        assertEquals(listOf("await:$PARENT_PID", "install", "relaunch"), fixture.events)
    }

    @Test
    fun `apply aborts without installing or relaunching when the installer fails verification`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val applier = fixture.applier()
        val wrongSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val result = applier.apply(UpdaterArguments(PARENT_PID, fixture.installer, fixture.app, wrongSha))

        assertFalse(result)
        // Waited for the parent, but never ran the installer or relaunched the tampered install.
        assertEquals(listOf("await:$PARENT_PID"), fixture.events)
        assertTrue(fixture.relaunched.isEmpty())
    }

    @Test
    fun `apply treats msiexec reboot-required as success`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp, installerExit = 3010)

        val result = fixture.applier().apply(UpdaterArguments(PARENT_PID, fixture.installer, fixture.app, expectedSha256 = null))

        assertTrue(result)
        assertEquals(listOf("await:$PARENT_PID", "install", "relaunch"), fixture.events)
    }

    @Test
    fun `apply opens the installer interactively instead of relaunching when the silent install fails`(
        @TempDir tmp: Path,
    ) {
        // 1639 = ERROR_INVALID_COMMAND_LINE, what msiexec returned for the old NSIS-style /S flag.
        val fixture = Fixture(tmp, installerExit = 1639)
        val applier = fixture.applier()

        val result = applier.apply(UpdaterArguments(PARENT_PID, fixture.installer, fixture.app, expectedSha256 = null))

        assertFalse(result)
        assertEquals(listOf("await:$PARENT_PID", "install", "interactive"), fixture.events)
        assertTrue(fixture.relaunched.isEmpty())
    }

    @Test
    fun `silentArgs are msiexec flags pinned to the running install directory`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)

        val args = UpdateApplier.silentArgs(fixture.installer, fixture.app)

        assertEquals(listOf("/qn", "/norestart", "/l*v"), args.take(3))
        assertEquals("INSTALLDIR=${tmp.toFile().absolutePath}", args.last())
    }

    private class Fixture(
        tmp: Path,
        private val installerExit: Int = 0,
    ) {
        val installer = File(tmp.toFile(), "cereal-client-latest.exe").apply { writeText("INSTALLER-BYTES") }
        val app = File(tmp.toFile(), "Cereal.exe").apply { writeText("APP") }
        val events = mutableListOf<String>()
        val relaunched = mutableListOf<File>()

        fun applier(): UpdateApplier =
            UpdateApplier(
                awaitProcessExit = { pid -> events += "await:$pid" },
                runInstaller = { _, _ ->
                    events += "install"
                    installerExit
                },
                openInteractively = { events += "interactive" },
                relaunch = { app ->
                    events += "relaunch"
                    relaunched += app
                },
            )
    }

    private companion object {
        const val PARENT_PID = 4242L
    }
}
