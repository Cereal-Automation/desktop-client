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

class MacAppBundleInstallerTest {
    @Test
    fun `runningAppBundlePath returns the provided path when set`() {
        val installer = MacAppBundleInstaller(bundlePathProvider = { "/Applications/Cereal.app" })

        assertEquals("/Applications/Cereal.app", installer.runningAppBundlePath())
    }

    @Test
    fun `runningAppBundlePath is null when the provider yields null or blank`() {
        assertNull(MacAppBundleInstaller(bundlePathProvider = { null }).runningAppBundlePath())
        assertNull(MacAppBundleInstaller(bundlePathProvider = { "  " }).runningAppBundlePath())
    }

    @Test
    fun `bundleFromJavaHome walks up to the app bundle for a jpackage layout`() {
        val javaHome = "/Applications/Cereal.app/Contents/runtime/Contents/Home"

        assertEquals("/Applications/Cereal.app", MacAppBundleInstaller.bundleFromJavaHome(javaHome))
    }

    @Test
    fun `bundleFromJavaHome is null for a non-bundle dev jvm and blank input`() {
        assertNull(MacAppBundleInstaller.bundleFromJavaHome("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"))
        assertNull(MacAppBundleInstaller.bundleFromJavaHome(null))
        assertNull(MacAppBundleInstaller.bundleFromJavaHome(""))
    }

    @Test
    fun `replaceAndRelaunch expands, verifies, swaps and relaunches on the happy path`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertTrue(result)
        // The bundle was expanded (ditto), quarantine stripped, both signature checks run, then swapped.
        assertTrue(fixture.ran(MacBinary.DITTO))
        assertTrue(fixture.ran(MacBinary.XATTR))
        assertTrue(fixture.ran(MacBinary.CODESIGN))
        assertTrue(fixture.ran(MacBinary.SPCTL))
        assertEquals(1, fixture.swaps.size)
        // Relaunched by `open -n <bundle>`, and the consumed download is cleaned up.
        assertEquals(listOf(listOf("/usr/bin/open", "-n", fixture.installedBundle.absolutePath)), fixture.launched)
        assertFalse(fixture.downloaded.exists())
    }

    @Test
    fun `replaceAndRelaunch passes the download through SHA verification when a digest is given`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()

        val result =
            installer.replaceAndRelaunch(
                fixture.downloaded,
                fixture.installedBundle,
                FileSha256.hash(fixture.downloaded),
            )

        assertTrue(result)
        assertEquals(1, fixture.swaps.size)
    }

    @Test
    fun `replaceAndRelaunch throws and never swaps when the download fails verification`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer()
        val wrongSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        assertThrows<UpdateVerificationException> {
            installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, wrongSha)
        }

        // Nothing beyond the verify ran: no ditto, no swap, no relaunch, and staging is cleaned up.
        assertFalse(fixture.ran(MacBinary.DITTO))
        assertTrue(fixture.swaps.isEmpty())
        assertTrue(fixture.launched.isEmpty())
        assertNoStagingLeftBehind(tmp)
    }

    @Test
    fun `replaceAndRelaunch returns false without expanding when the install location is not writable`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(writable = { false })

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertFalse(result)
        assertFalse(fixture.ran(MacBinary.DITTO))
        assertTrue(fixture.swaps.isEmpty())
        assertTrue(fixture.launched.isEmpty())
    }

    @Test
    fun `replaceAndRelaunch swaps when the parent is writable even though the bundle itself is not`(
        @TempDir tmp: Path,
    ) {
        // On macOS 13+ an installed .app in /Applications carries a com.apple.macl provenance
        // attribute, so Files.isWritable reports the bundle non-writable even for its owner. The
        // renamex_np swap only needs write on the parent directory, so this must NOT block the
        // self-install — the precondition is parent-writability, not bundle-writability.
        val fixture = Fixture(tmp)
        val installer =
            fixture.installer(writable = { file -> file.absolutePath != fixture.installedBundle.absolutePath })

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertTrue(result)
        assertEquals(1, fixture.swaps.size)
        assertEquals(listOf(listOf("/usr/bin/open", "-n", fixture.installedBundle.absolutePath)), fixture.launched)
    }

    @Test
    fun `replaceAndRelaunch returns false without swapping when ditto expansion fails`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(dittoExpands = false)

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertFalse(result)
        assertTrue(fixture.swaps.isEmpty())
        assertTrue(fixture.launched.isEmpty())
        assertNoStagingLeftBehind(tmp)
    }

    @Test
    fun `replaceAndRelaunch returns false and does not swap when the signature assertion fails`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(codesignExit = 1)

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertFalse(result)
        assertTrue(fixture.ran(MacBinary.DITTO))
        assertTrue(fixture.swaps.isEmpty())
        assertTrue(fixture.launched.isEmpty())
        assertNoStagingLeftBehind(tmp)
    }

    @Test
    fun `replaceAndRelaunch returns false and does not swap when the Gatekeeper assessment fails`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(spctlExit = 1)

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertFalse(result)
        assertTrue(fixture.ran(MacBinary.SPCTL))
        assertTrue(fixture.swaps.isEmpty())
        assertTrue(fixture.launched.isEmpty())
        assertNoStagingLeftBehind(tmp)
    }

    @Test
    fun `replaceAndRelaunch returns false and does not relaunch when the swap fails`(
        @TempDir tmp: Path,
    ) {
        val fixture = Fixture(tmp)
        val installer = fixture.installer(swapSucceeds = false)

        val result = installer.replaceAndRelaunch(fixture.downloaded, fixture.installedBundle, expectedSha256 = null)

        assertFalse(result)
        assertEquals(1, fixture.swaps.size)
        assertTrue(fixture.launched.isEmpty())
        assertNoStagingLeftBehind(tmp)
    }

    private fun assertNoStagingLeftBehind(tmp: Path) {
        val leftovers = tmp.toFile().listFiles()!!.filter { it.isDirectory && it.name.startsWith(".Cereal.app-") }
        assertTrue(leftovers.isEmpty(), "staging dir should be cleaned up: $leftovers")
    }

    private enum class MacBinary(
        val path: String,
    ) {
        DITTO("/usr/bin/ditto"),
        XATTR("/usr/bin/xattr"),
        CODESIGN("/usr/bin/codesign"),
        SPCTL("/usr/sbin/spctl"),
    }

    /**
     * Wires a [MacAppBundleInstaller] against real temp files but fake OS seams. The `ditto` fake
     * materializes the staged `.app` directory (so the `isDirectory` gate passes), and the swap is
     * recorded rather than performed via the real syscall.
     */
    private class Fixture(
        tmp: Path,
    ) {
        val installedBundle = File(tmp.toFile(), "Cereal.app").apply { mkdirs() }
        val downloaded = File(tmp.toFile(), "cereal-client-latest.zip").apply { writeText("NEW-BUNDLE-BYTES") }
        val commands = mutableListOf<List<String>>()
        val launched = mutableListOf<List<String>>()
        val swaps = mutableListOf<Pair<String, String>>()

        fun ran(binary: MacBinary) = commands.any { it.firstOrNull() == binary.path }

        fun installer(
            dittoExpands: Boolean = true,
            codesignExit: Int = 0,
            spctlExit: Int = 0,
            swapSucceeds: Boolean = true,
            writable: (File) -> Boolean = { true },
        ): MacAppBundleInstaller =
            MacAppBundleInstaller(
                bundlePathProvider = { installedBundle.absolutePath },
                commandRunner = { command ->
                    commands += command
                    when (command.firstOrNull()) {
                        MacBinary.DITTO.path -> {
                            // `ditto -x -k <zip> <stageDir>`: materialize the staged .app so the
                            // installer's isDirectory gate passes, mirroring a real expansion.
                            if (dittoExpands) {
                                File(command.last(), installedBundle.name).mkdirs()
                                0
                            } else {
                                1
                            }
                        }

                        MacBinary.CODESIGN.path -> {
                            codesignExit
                        }

                        MacBinary.SPCTL.path -> {
                            spctlExit
                        }

                        else -> {
                            0
                        }
                    }
                },
                nativeSwap = { from, to ->
                    swaps += from to to
                    swapSucceeds
                },
                processLauncher = { command -> launched += command },
                writable = writable,
            )
    }
}
