package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.SelfUpdateException
import com.cereal.client.infrastructure.data.FileSha256
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Self-installs Windows updates by spawning a detached [UpdateApplier] helper.
 *
 * The running `.exe` and everything under the install directory are locked while the app is alive,
 * so — unlike Linux/macOS — the swap cannot happen in-process. Instead this spawns a detached,
 * console-less `javaw` process running the app's own [com.cereal.client.updater.UpdaterMain] entry
 * point in "updater mode": it waits for this app to exit (releasing the locks), then silently
 * reinstalls the signed installer and relaunches. Because the helper is the app-image's own bundled
 * `javaw.exe` run against the app classpath, there is **no new build artifact** — only the reused
 * signed `.exe` installer.
 *
 * We drive the existing signed jpackage NSIS installer (`installer.exe /S`) rather than a raw
 * file-copy: it already overwrites the app image and refreshes Start-menu/shortcut/uninstall
 * entries, and under `perUserInstall = true` it targets a user-writable location with no UAC. If the
 * install is per-machine (`Program Files`) or its path is undetectable, a silent reinstall would
 * need elevation, so this returns false and the caller falls back to the interactive installer — the
 * no-elevation boundary.
 *
 * The OS-touching seams are injectable so the spawn logic is unit-testable without launching a real
 * process. Whether the detached `javaw` truly outlives the parent with no console is one of the
 * per-OS validation items that require a real Windows runner.
 */
class WindowsUpdateInstaller(
    private val appPathProvider: () -> String? = { System.getProperty(JPACKAGE_APP_PATH) },
    private val currentPidProvider: () -> Long = { ProcessHandle.current().pid() },
    private val javawResolver: (File) -> File? = ::defaultJavaw,
    private val classpathProvider: () -> String = { System.getProperty("java.class.path").orEmpty() },
    private val writable: (File) -> Boolean = { Files.isWritable(it.toPath()) },
    private val helperLauncher: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
) {
    private val logger = LoggerFactory.getLogger(WindowsUpdateInstaller::class.java)

    /**
     * Absolute path of the launcher this process was started from (jpackage's `jpackage.app-path`),
     * or null when it is not set (e.g. a dev run) and there is therefore no install to self-update.
     */
    fun runningAppPath(): String? = appPathProvider()?.takeIf { it.isNotBlank() }

    /**
     * Spawns the detached updater helper that will apply [installer] to the install at [app] after
     * this process exits, then relaunch. Returns true once the helper is spawned — the caller should
     * exit so the helper can proceed. Returns false on a precondition miss (per-machine install / no
     * bundled `javaw` / spawn failure) so the caller falls back to the interactive installer. A
     * SHA-256 mismatch on [expectedSha256] throws [UpdateVerificationException].
     *
     * The installer is verified here before the helper is spawned, and again by the helper after the
     * app exits: the two checks bracket the window in which the file sits on disk unattended.
     */
    fun spawnUpdater(
        installer: File,
        app: File,
        expectedSha256: String?,
    ): Boolean {
        val installDir = app.absoluteFile.parentFile
        if (installDir == null) {
            logger.warn("Windows app path $app has no parent directory; cannot self-install")
            return false
        }
        // No-elevation boundary: a per-machine install under Program Files is not user-writable, so a
        // silent /S reinstall there would require UAC. Degrade to the interactive installer instead.
        if (!writable(app) || !writable(installDir)) {
            logger.warn("Windows install at $app is not user-writable; skipping silent self-install")
            return false
        }

        val javaw = javawResolver(app)
        if (javaw == null || !javaw.exists()) {
            // Reaching here means we are a real jpackage install (runningAppPath gated on
            // jpackage.app-path) whose bundled JVM is missing — a broken image, not expected
            // degradation, so surface it to Sentry rather than silently degrading.
            val message = "Bundled javaw.exe not found for $app; cannot spawn updater helper"
            logger.warn(message)
            CrashReporter.report(SelfUpdateException(message))
            return false
        }

        FileSha256.verifyMatch(installer, expectedSha256, "Windows update")

        val command =
            listOf(javaw.absolutePath, "-cp", classpathProvider(), UPDATER_MAIN_CLASS) +
                UpdaterArguments(currentPidProvider(), installer, app, expectedSha256).toArgs()
        return try {
            helperLauncher(command)
            true
        } catch (e: IOException) {
            logger.error("Failed to spawn the Windows updater helper", e)
            CrashReporter.report(e)
            false
        }
    }

    companion object {
        /** jpackage sets this to the running launcher's absolute path — the Windows analogue of `$APPIMAGE`. */
        const val JPACKAGE_APP_PATH = "jpackage.app-path"

        /** Fully-qualified name of the reflectively-launched updater entry point (kept by ProGuard). */
        const val UPDATER_MAIN_CLASS = "com.cereal.client.updater.UpdaterMain"

        /**
         * A jpackage Windows app runs from `<install>\<App>.exe` with its JVM at
         * `<install>\runtime\bin\javaw.exe`. Returns that windowless launcher next to the app.
         */
        private fun defaultJavaw(app: File): File? = app.absoluteFile.parentFile?.let { File(it, "runtime/bin/javaw.exe") }
    }
}
