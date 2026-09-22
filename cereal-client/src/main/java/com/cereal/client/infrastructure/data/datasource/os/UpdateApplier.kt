package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.domain.model.exception.UpdateVerificationException
import com.cereal.client.infrastructure.data.FileSha256
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Core of the Windows update helper: the sequence a detached, console-less `javaw` process runs
 * *after* the main app has exited, to apply an update that could not be applied in-process because
 * the running `.exe` and its install directory are locked while the app is alive.
 *
 *  1. Wait for the parent (the app) to fully exit — this is what releases the file locks; no fixed
 *     sleep, no exit race.
 *  2. Re-verify the installer's SHA-256. The process that hash-checked the download is gone and the
 *     file has sat on disk since, so it is re-checked here (CWE-367). A mismatch aborts without
 *     relaunching — a tampered install is never applied or launched.
 *  3. Run the signed installer silently (`installer.exe /S`), a per-user reinstall needing no UAC.
 *  4. Relaunch the freshly installed app.
 *
 * This runs with no UI (the app is gone), so failures are logged and reported via the boolean
 * return rather than surfaced to the user. All process/IO effects are injectable seams so the
 * orchestration is unit-testable without spawning real processes.
 */
class UpdateApplier(
    private val awaitProcessExit: (Long) -> Unit = { pid ->
        ProcessHandle.of(pid).ifPresent { it.onExit().join() }
    },
    private val runInstaller: (File) -> Int = { installer ->
        ProcessBuilder(installer.absolutePath, SILENT_FLAG).inheritIO().start().waitFor()
    },
    private val relaunch: (File) -> Unit = { app -> ProcessBuilder(app.absolutePath).start() },
) {
    private val logger = LoggerFactory.getLogger(UpdateApplier::class.java)

    /**
     * Applies the update described by [arguments] after its parent process exits. Returns true when
     * the installer succeeded and the app was relaunched; false (without relaunching) on a
     * verification or installer failure.
     */
    fun apply(arguments: UpdaterArguments): Boolean {
        awaitProcessExit(arguments.parentPid)
        if (!verify(arguments.installer, arguments.expectedSha256)) return false
        if (!runSilently(arguments.installer)) return false
        return relaunchApp(arguments.app)
    }

    /** Runs the silent installer; returns true only when it launched and exited 0. */
    private fun runSilently(installer: File): Boolean {
        val exitCode =
            try {
                runInstaller(installer)
            } catch (e: IOException) {
                logger.error("Failed to run the silent Windows installer ${installer.path}", e)
                return false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        if (exitCode != 0) {
            logger.error("Silent Windows installer exited with $exitCode; not relaunching")
            return false
        }
        return true
    }

    private fun relaunchApp(app: File): Boolean =
        try {
            relaunch(app)
            true
        } catch (e: IOException) {
            logger.error("Update applied but relaunch of ${app.path} failed", e)
            false
        }

    /**
     * Re-verifies the installer using the same digest-compare as every other self-installer
     * ([FileSha256.verifyMatch]); the only difference is that this helper has no UI, so a mismatch
     * is logged and turned into a `false` return (abort without relaunch) rather than surfaced as an
     * exception.
     */
    private fun verify(
        installer: File,
        expectedSha256: String?,
    ): Boolean =
        try {
            FileSha256.verifyMatch(installer, expectedSha256, "Windows installer")
            true
        } catch (e: UpdateVerificationException) {
            logger.error("Aborting Windows update: ${e.message}", e)
            false
        }

    companion object {
        /** NSIS silent-install switch: no window, no click-through. */
        const val SILENT_FLAG = "/S"
    }
}
