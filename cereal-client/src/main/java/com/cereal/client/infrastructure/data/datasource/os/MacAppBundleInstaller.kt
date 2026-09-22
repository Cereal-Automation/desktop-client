package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.SelfUpdateException
import com.cereal.client.domain.model.exception.UpdateVerificationException
import com.cereal.client.infrastructure.data.FileSha256
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Self-installs macOS `.app` bundle updates, mirroring [AppImageInstaller] on Linux.
 *
 * The auto-update artifact is a `.zip` of the already-notarized+stapled `.app` (see the cross-OS
 * self-update spec). "Installing" means expanding that zip and swapping the new bundle over the
 * running one — no `.pkg`, no drag-to-Applications, no privilege escalation. The stapled
 * notarization ticket travels *inside* the `.app`, so the replaced bundle validates with Gatekeeper
 * offline; there is no re-notarization at update time.
 *
 * The sequence (staging in the installed bundle's *own parent directory* so the final exchange stays
 * on one volume and is therefore atomic):
 *
 *  1. Re-verify the downloaded zip's SHA-256 — mismatch is a hard stop ([UpdateVerificationException]),
 *     never a false return that would fall back to launching a tampered download (CWE-367).
 *  2. Expand with `ditto -x -k`, which preserves the Developer ID signature, framework symlinks and
 *     the embedded ticket (plain unzip mangles symlinks/xattrs and invalidates the signature).
 *  3. Strip `com.apple.quarantine` recursively — done *after* our own verification, so it is strictly
 *     stronger than Gatekeeper's first-launch prompt and avoids App Translocation running the app
 *     from a randomized read-only path.
 *  4. Assert the staged bundle with `codesign --verify --deep --strict` and `spctl --assess`.
 *  5. Atomically exchange the staged bundle with the running one via `renamex_np(RENAME_SWAP)`.
 *  6. Relaunch with `open -n`.
 *
 * A precondition miss (bundle path undetectable, or the install *location* — the parent directory —
 * is not user-writable, the no-elevation boundary) returns false so the caller falls back to
 * revealing the download; only a SHA-256 mismatch throws. Note the bundle's own writability is not a
 * precondition: the swap operates on the parent directory, and on macOS 13+ an installed bundle's
 * com.apple.macl provenance makes [writable] report false for it regardless of ownership. The OS-touching seams ([bundlePathProvider], [commandRunner],
 * [nativeSwap], [processLauncher], [writable]) are injectable so the orchestration is unit-testable
 * without spawning `ditto`/`codesign` or issuing the real swap syscall.
 */
class MacAppBundleInstaller(
    private val bundlePathProvider: () -> String? = { defaultRunningBundlePath() },
    private val commandRunner: (List<String>) -> Int = ::runToExitCode,
    private val nativeSwap: (String, String) -> Boolean = MacFileSwap::swap,
    private val processLauncher: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
    private val writable: (File) -> Boolean = { Files.isWritable(it.toPath()) },
) {
    private val logger = LoggerFactory.getLogger(MacAppBundleInstaller::class.java)

    /**
     * Absolute path of the `.app` bundle this process is running from, or null when the client is not
     * running as an installed macOS bundle (e.g. a dev run), in which case there is nothing to
     * self-replace.
     */
    fun runningAppBundlePath(): String? = bundlePathProvider()?.takeIf { it.isNotBlank() }

    /**
     * Replaces the `.app` at [installedBundle] with the bundle inside the downloaded [downloaded] zip
     * and relaunches it. Returns true when the swap succeeded and the new instance was launched — the
     * caller should then exit so the relaunched one takes over. Returns false (leaving
     * [installedBundle] untouched) on any precondition miss or step failure, so the caller can fall
     * back to a manual install. A SHA-256 mismatch on [expectedSha256] throws
     * [UpdateVerificationException] rather than returning false.
     */
    fun replaceAndRelaunch(
        downloaded: File,
        installedBundle: File,
        expectedSha256: String?,
    ): Boolean {
        val parent = installedBundle.absoluteFile.parentFile
        // No-elevation boundary: the renamex_np swap exchanges two entries *inside the parent
        // directory*, so the only permission it needs is write on that parent. We deliberately do
        // NOT gate on the bundle's own writability: on macOS 13+ an installed .app carries a
        // com.apple.macl provenance attribute that makes Files.isWritable report false for the
        // bundle even for its owner, which would spuriously block every real self-install. A
        // standard (non-admin) user still can't write a root:admin /Applications, so parent
        // writability remains the correct degrade-to-manual boundary; we never prompt for admin.
        if (parent == null || !writable(parent)) {
            logger.warn("${installedBundle.parent} is missing or not user-writable; skipping macOS self-install")
            return false
        }

        val stageDir =
            try {
                // Same directory as the target ⇒ same volume ⇒ the renamex_np exchange is atomic.
                Files.createTempDirectory(parent.toPath(), ".${installedBundle.name}-").toFile()
            } catch (e: IOException) {
                logger.error("Failed to create staging directory next to ${installedBundle.path}", e)
                CrashReporter.report(e)
                return false
            }

        return try {
            swapInVerifiedBundle(downloaded, stageDir, installedBundle, expectedSha256)
        } catch (e: UpdateVerificationException) {
            stageDir.deleteRecursively()
            throw e
        } catch (e: IOException) {
            logger.error("Failed to self-install macOS update to ${installedBundle.path}", e)
            CrashReporter.report(e)
            stageDir.deleteRecursively()
            false
        }
    }

    /**
     * Expands, verifies and swaps the downloaded bundle in [stageDir], relaunching on success.
     * Deletes [stageDir] and returns false on any step failure; a SHA-256 mismatch propagates as
     * [UpdateVerificationException]. Extracted from [replaceAndRelaunch] so each stays within the
     * return-count budget and the outer function owns only the guard/staging/cleanup framing.
     */
    private fun swapInVerifiedBundle(
        downloaded: File,
        stageDir: File,
        installedBundle: File,
        expectedSha256: String?,
    ): Boolean {
        FileSha256.verifyMatch(downloaded, expectedSha256, "macOS update")

        val stagedApp = File(stageDir, installedBundle.name)
        if (commandRunner(listOf(DITTO, "-x", "-k", downloaded.absolutePath, stageDir.absolutePath)) != 0 ||
            !stagedApp.isDirectory
        ) {
            return reportSelfInstallFailure(stageDir, "Failed to expand macOS update zip into $stageDir")
        }

        // Strip quarantine after verification. A non-zero exit here just means the attribute was
        // absent, which is fine, so its result is not gated on.
        commandRunner(listOf(XATTR, "-dr", QUARANTINE_ATTR, stagedApp.absolutePath))

        assertAndSwapStagedBundle(stagedApp, installedBundle)?.let { failure ->
            return reportSelfInstallFailure(stageDir, failure)
        }

        // After the swap, stageDir holds the *old* bundle; remove it and the consumed download.
        if (!downloaded.delete()) {
            logger.debug("Could not delete consumed download ${downloaded.path}")
        }
        stageDir.deleteRecursively()
        processLauncher(listOf(OPEN, "-n", installedBundle.absolutePath))
        return true
    }

    /**
     * Asserts the staged bundle and, if it passes, performs the atomic swap. Returns null on success
     * or a human-readable reason for the first failed step. Each step is checked individually rather
     * than as one combined condition because which one fails is diagnostically distinct — a codesign
     * failure implies a corrupt/tampered bundle that nonetheless matched its SHA, an spctl failure is
     * a Gatekeeper rejection, and a swap failure is the renamex_np edge (e.g. App Management on the
     * live bundle). Short-circuit order is preserved: we never swap a bundle that failed validation.
     */
    private fun assertAndSwapStagedBundle(
        stagedApp: File,
        installedBundle: File,
    ): String? {
        if (commandRunner(listOf(CODESIGN, "--verify", "--deep", "--strict", stagedApp.absolutePath)) != 0) {
            return "Staged macOS bundle failed codesign verification; not relaunching ${installedBundle.path}"
        }
        if (commandRunner(listOf(SPCTL, "--assess", "--type", "execute", stagedApp.absolutePath)) != 0) {
            return "Staged macOS bundle failed Gatekeeper assessment; not relaunching ${installedBundle.path}"
        }
        if (!nativeSwap(stagedApp.absolutePath, installedBundle.absolutePath)) {
            return "Atomic swap of the staged macOS bundle failed; not relaunching ${installedBundle.path}"
        }
        return null
    }

    /**
     * Logs [message], reports it to Sentry as a [SelfUpdateException] so post-precondition mechanism
     * failures are visible in the field, cleans up [stageDir], and returns false for the caller to
     * hand off to the manual fallback.
     */
    private fun reportSelfInstallFailure(
        stageDir: File,
        message: String,
    ): Boolean {
        logger.warn(message)
        CrashReporter.report(SelfUpdateException(message))
        stageDir.deleteRecursively()
        return false
    }

    companion object {
        private const val DITTO = "/usr/bin/ditto"
        private const val XATTR = "/usr/bin/xattr"
        private const val CODESIGN = "/usr/bin/codesign"
        private const val SPCTL = "/usr/sbin/spctl"
        private const val OPEN = "/usr/bin/open"
        private const val QUARANTINE_ATTR = "com.apple.quarantine"

        /**
         * A jpackage macOS app runs its JVM from `<App>.app/Contents/runtime/Contents/Home`, so the
         * bundle root is four parents up from `java.home`. Returns null when the shape does not match
         * (dev run / non-installed), analogous to Linux's absent `$APPIMAGE`.
         */
        private const val JAVA_HOME_TO_BUNDLE_DEPTH = 4

        private fun defaultRunningBundlePath(): String? {
            val candidate = bundleFromJavaHome(System.getProperty("java.home")) ?: return null
            // Confirm it really is an app bundle before we treat it as self-replaceable.
            return candidate.takeIf { File(it, "Contents/MacOS").isDirectory }
        }

        /**
         * Pure path derivation (no filesystem access), extracted so it can be unit-tested: walks
         * [JAVA_HOME_TO_BUNDLE_DEPTH] parents up from [javaHome] and returns the result only when it
         * ends in `.app`.
         */
        internal fun bundleFromJavaHome(javaHome: String?): String? {
            if (javaHome.isNullOrBlank()) return null
            var current: File? = File(javaHome)
            repeat(JAVA_HOME_TO_BUNDLE_DEPTH) { current = current?.parentFile }
            val bundle = current ?: return null
            return bundle.absolutePath.takeIf { bundle.name.endsWith(".app") }
        }

        private const val COMMAND_FAILED = -1

        private fun runToExitCode(command: List<String>): Int =
            try {
                ProcessBuilder(command).inheritIO().start().waitFor()
            } catch (e: IOException) {
                val log = LoggerFactory.getLogger(MacAppBundleInstaller::class.java)
                log.error("Failed to run ${command.firstOrNull()}", e)
                COMMAND_FAILED
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                COMMAND_FAILED
            }
    }
}
