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
 *  3. Run the signed installer silently, a per-user reinstall needing no UAC. The installer is
 *     jpackage's `exe` wrapper, which extracts its embedded MSI and runs `msiexec /i <msi> <args>`
 *     with our arguments appended, so the flags are msiexec's ([silentArgs]) and the exit code is
 *     msiexec's.
 *  4. Relaunch the freshly installed app.
 *
 * If the silent install fails, the installer is opened interactively instead so the user sees what
 * went wrong and can finish by hand. Relaunching the old app instead could loop: a mandatory update
 * at bootstrap would immediately spawn this helper again. All process/IO effects are injectable
 * seams so the orchestration is unit-testable without spawning real processes.
 */
class UpdateApplier(
    private val awaitProcessExit: (Long) -> Unit = { pid ->
        ProcessHandle.of(pid).ifPresent { it.onExit().join() }
    },
    private val runInstaller: (installer: File, app: File) -> Int = { installer, app ->
        ProcessBuilder(listOf(installer.absolutePath) + silentArgs(installer, app)).inheritIO().start().waitFor()
    },
    private val openInteractively: (File) -> Unit = { installer -> ProcessBuilder(installer.absolutePath).start() },
    private val relaunch: (File) -> Unit = { app -> ProcessBuilder(app.absolutePath).directory(app.parentFile).start() },
) {
    private val logger = LoggerFactory.getLogger(UpdateApplier::class.java)

    /**
     * Applies the update described by [arguments] after its parent process exits. Returns true when
     * the installer succeeded and the app was relaunched; false on a verification failure (nothing
     * is run) or an installer failure (the installer is opened interactively instead).
     */
    fun apply(arguments: UpdaterArguments): Boolean {
        awaitProcessExit(arguments.parentPid)
        if (!verify(arguments.installer, arguments.expectedSha256)) return false
        if (!runSilently(arguments.installer, arguments.app)) {
            openInstallerInteractively(arguments.installer)
            return false
        }
        return relaunchApp(arguments.app)
    }

    /** Runs the silent installer; returns true only when it launched and msiexec reported success. */
    private fun runSilently(
        installer: File,
        app: File,
    ): Boolean {
        val exitCode =
            try {
                runInstaller(installer, app)
            } catch (e: IOException) {
                logger.error("Failed to run the silent Windows installer ${installer.path}", e)
                return false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        if (exitCode !in SUCCESS_EXIT_CODES) {
            logger.error("Silent Windows installer exited with $exitCode; see ${installLog(installer).path}")
            return false
        }
        return true
    }

    private fun openInstallerInteractively(installer: File) {
        try {
            openInteractively(installer)
        } catch (e: IOException) {
            logger.error("Could not open the Windows installer ${installer.path} interactively", e)
        }
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
        /** msiexec success codes: 0 = done, 3010 = done but a reboot is pending (ERROR_SUCCESS_REBOOT_REQUIRED). */
        val SUCCESS_EXIT_CODES = setOf(0, 3010)

        /**
         * msiexec arguments for an unattended reinstall: no UI, never reboot on its own, a verbose log
         * next to the installer for diagnosing failures, and `INSTALLDIR` pinned to the running install
         * so a custom location picked in the installer's directory chooser is upgraded in place.
         */
        fun silentArgs(
            installer: File,
            app: File,
        ): List<String> =
            listOf(
                "/qn",
                "/norestart",
                "/l*v",
                installLog(installer).absolutePath,
                "INSTALLDIR=${app.absoluteFile.parentFile.absolutePath}",
            )

        private fun installLog(installer: File) = File(installer.absoluteFile.parentFile, "cereal-update-msiexec.log")
    }
}
