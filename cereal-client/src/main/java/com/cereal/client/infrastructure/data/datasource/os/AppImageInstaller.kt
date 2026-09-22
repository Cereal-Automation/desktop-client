package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.exception.UpdateVerificationException
import com.cereal.client.infrastructure.data.FileSha256
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Self-installs Linux AppImage updates.
 *
 * An AppImage is a single self-contained executable, so "installing" an update is simply replacing
 * the running image file with the freshly downloaded one and launching it again — no package
 * manager and no privilege escalation. The AppImage runtime exports the absolute path of the
 * running image in the `APPIMAGE` environment variable; that is the file we replace.
 *
 * The replace is done by staging the download in the *target's own directory* and then doing a
 * single atomic rename over the target. Renaming (rather than writing in place) avoids `ETXTBSY`
 * ("text file busy") on the still-running executable, and staging in the same directory keeps the
 * final move on one filesystem so it can be atomic even though the download lives under the temp
 * dir (often a separate mount).
 *
 * The OS-touching seams ([appImagePathProvider], [processLauncher]) are injectable so the
 * replace/relaunch logic can be exercised against real temp files without spawning a process.
 */
class AppImageInstaller(
    private val appImagePathProvider: () -> String? = { System.getenv(APPIMAGE_ENV) },
    private val processLauncher: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
) {
    private val logger = LoggerFactory.getLogger(AppImageInstaller::class.java)

    /**
     * Absolute path of the AppImage this process is running from, or null when the client is not
     * running as an AppImage (e.g. a `.deb`/dev run), in which case there is nothing to self-replace.
     */
    fun runningAppImagePath(): String? = appImagePathProvider()?.takeIf { it.isNotBlank() }

    /**
     * Replaces the AppImage at [target] with the contents of [downloaded] and launches the new
     * image. Returns true if the replacement succeeded and the new process was started — the caller
     * should then terminate the current process so the relaunched one takes over. Returns false
     * (leaving [target] untouched) on any IO failure, so the caller can fall back to a manual
     * install.
     *
     * When [expectedSha256] is provided, the staged copy — the exact bytes that will be renamed
     * onto [target] and executed — is re-hashed right before the atomic move. On mismatch the
     * staged copy is discarded and [UpdateVerificationException] is thrown (never a false return,
     * which would let the caller fall back to launching the tampered download): the download was
     * hash-checked while streaming, but it sat on disk since then (CWE-367).
     */
    fun replaceAndRelaunch(
        downloaded: File,
        target: File,
        expectedSha256: String?,
    ): Boolean {
        val targetDir = target.absoluteFile.parentFile
        if (targetDir == null) {
            logger.warn("AppImage target $target has no parent directory; cannot self-install")
            return false
        }

        val staged: File
        try {
            staged = File.createTempFile(".${target.name}-", ".new", targetDir)
        } catch (e: IOException) {
            logger.error("Failed to stage AppImage update next to ${target.path}", e)
            CrashReporter.report(e)
            return false
        }

        return try {
            Files.copy(downloaded.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
            verifyStagedImage(staged, expectedSha256)
            // AppImages must be executable to run; ownerOnly = false so all users can launch it.
            staged.setExecutable(true, false)
            // Atomic rename within the same directory: avoids ETXTBSY on the running image and never
            // leaves a half-written executable at the target path.
            Files.move(
                staged.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            // The download has been consumed; best-effort cleanup.
            if (!downloaded.delete()) {
                logger.debug("Could not delete consumed download ${downloaded.path}")
            }
            processLauncher(listOf(target.absolutePath))
            true
        } catch (e: IOException) {
            logger.error("Failed to self-install AppImage to ${target.path}", e)
            CrashReporter.report(e)
            staged.delete()
            false
        }
    }

    /**
     * Re-hashes [staged] against [expectedSha256] (skipped when null). Deletes [staged] and throws
     * [UpdateVerificationException] on mismatch or when the file cannot be read — aborting the
     * install entirely rather than returning false into the manual-install fallback.
     */
    private fun verifyStagedImage(
        staged: File,
        expectedSha256: String?,
    ) {
        if (expectedSha256 == null) return
        val actualSha256 =
            try {
                FileSha256.hash(staged)
            } catch (e: IOException) {
                staged.delete()
                throw UpdateVerificationException("Could not read the staged AppImage update for verification", e)
            }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            staged.delete()
            throw UpdateVerificationException(
                "AppImage update failed SHA-256 verification before launch: " +
                    "expected $expectedSha256 but was $actualSha256",
            )
        }
    }

    companion object {
        /** Set by the AppImage runtime to the absolute path of the running image. */
        const val APPIMAGE_ENV = "APPIMAGE"
    }
}
