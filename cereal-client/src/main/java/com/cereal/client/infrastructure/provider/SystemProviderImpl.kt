package com.cereal.client.infrastructure.provider

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.model.url.WebUrl
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.infrastructure.data.FileSha256
import com.cereal.client.infrastructure.data.datasource.os.AppImageInstaller
import com.cereal.client.infrastructure.data.datasource.os.BrowserDataSource
import com.cereal.client.infrastructure.data.datasource.os.MacAppBundleInstaller
import com.cereal.client.infrastructure.data.datasource.os.NotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.WindowsUpdateInstaller
import com.cereal.client.presentation.util.ImageUtil
import org.slf4j.LoggerFactory
import java.io.File

class SystemProviderImpl(
    private val applicationConfig: ApplicationConfig,
    private val notificationDataSource: NotificationDataSource,
    private val browserDataSource: BrowserDataSource,
    private val appImageInstaller: AppImageInstaller,
    private val macAppBundleInstaller: MacAppBundleInstaller,
    private val windowsUpdateInstaller: WindowsUpdateInstaller,
) : SystemProvider {
    private val logger = LoggerFactory.getLogger(SystemProviderImpl::class.java)
    private val isLinux = applicationConfig.operatingSystem == OperatingSystemType.Linux
    private val isMacOs = applicationConfig.operatingSystem == OperatingSystemType.MacOS
    private val isWindows = applicationConfig.operatingSystem == OperatingSystemType.Windows
    private val shouldAttemptXdg = isLinux

    override suspend fun createTrayIcon() {
        ImageUtil.loadImageResource(SystemProviderImpl::class.java, "/${applicationConfig.appIcon}")?.let { icon ->
            notificationDataSource.createTrayIcon(icon, applicationConfig.title)
        }
    }

    override suspend fun browser(url: String) {
        if (url.isEmpty()) {
            logger.warn("browse() called with invalid input")
            return
        }

        // Only web URLs may be handed to the OS opener. The url can originate from untrusted input
        // (e.g. a marketplace script manifest's supportUrl), and xdg-open / Desktop#browse will
        // happily dispatch other schemes — file://, smb://, or any registered protocol handler —
        // which is a one-click vector for credential leaks and local-file access. Reject anything
        // that is not http(s) before it reaches a handler.
        val webUrl = WebUrl.parse(url)
        if (webUrl == null) {
            logger.warn("browse() refused a non-http(s) URL")
            return
        }
        val safeUrl = webUrl.value

        // On Linux, java.awt.Desktop#browse has the same unreliability as Desktop#open: it can
        // report success while doing nothing because the JDK delegates to legacy gnome-open/
        // gvfs-open helpers absent on modern desktops. Prefer xdg-open there and fall back to
        // Desktop#browse only if it is unavailable.
        if (shouldAttemptXdg && browserDataSource.attemptXdgOpen(safeUrl)) {
            logger.debug("Opened url through xdg-open to $safeUrl")
            return
        }

        if (browserDataSource.attemptDesktopBrowse(safeUrl)) {
            logger.debug("Opened url through Desktop#browse to $safeUrl")
            return
        }
    }

    override suspend fun open(directory: File): OpenFileResult {
        if (directory.path.isEmpty()) {
            logger.warn("open() called with invalid input")
            return OpenFileResult.Failed
        }

        // On Linux, java.awt.Desktop#open is unreliable: isSupported(OPEN) can report true and
        // open() can return without throwing while doing nothing, because the JDK delegates to
        // legacy gnome-open/gvfs-open helpers that are absent on modern desktops. That silent
        // no-op would make the downloaded update never open, so prefer xdg-open there and fall
        // back to Desktop#open only if it is unavailable.
        if (attemptOpen(directory)) {
            logger.debug("Opened $directory")
            return OpenFileResult.Opened
        }

        // Opening the file itself failed — e.g. on Linux when no application is registered for the
        // downloaded installer. Reveal its containing folder in the file manager so the user can
        // open it manually.
        logger.warn("open() could not open $directory, revealing its location instead")
        return if (revealInFileManager(directory)) {
            OpenFileResult.Revealed
        } else {
            logger.warn("open() could not reveal $directory")
            OpenFileResult.Failed
        }
    }

    /**
     * Opens [target] with the system handler, preferring xdg-open on Linux and falling back to
     * java.awt.Desktop#open elsewhere (and as a last resort on Linux).
     */
    private fun attemptOpen(target: File): Boolean =
        (shouldAttemptXdg && browserDataSource.attemptXdgOpen(target.absolutePath)) ||
            browserDataSource.attemptDesktopOpen(target)

    /**
     * Opens the folder containing [file] so the user can see and launch it manually. Uses the same
     * primitives as [open], applied to the parent directory.
     */
    private fun revealInFileManager(file: File): Boolean {
        val parent = file.absoluteFile.parentFile ?: return false
        val revealed = attemptOpen(parent)
        if (revealed) {
            logger.debug("Revealed $file by opening folder $parent")
        }
        return revealed
    }

    override suspend fun installUpdate(
        installer: File,
        expectedSha256: String?,
    ): UpdateInstallResult {
        if (isLinux) {
            val runningAppImage = appImageInstaller.runningAppImagePath()
            if (runningAppImage != null &&
                appImageInstaller.replaceAndRelaunch(installer, File(runningAppImage), expectedSha256)
            ) {
                logger.info("Replaced running AppImage with update and relaunched")
                return UpdateInstallResult.Relaunching
            }
            logger.warn("AppImage self-install unavailable or failed; handing the download to the OS")
        }

        if (isMacOs) {
            val runningBundle = macAppBundleInstaller.runningAppBundlePath()
            if (runningBundle != null &&
                macAppBundleInstaller.replaceAndRelaunch(installer, File(runningBundle), expectedSha256)
            ) {
                logger.info("Replaced running macOS app bundle with update and relaunched")
                return UpdateInstallResult.Relaunching
            }
            logger.warn("macOS self-install unavailable or failed; handing the download to the OS")
        }

        if (isWindows) {
            val runningApp = windowsUpdateInstaller.runningAppPath()
            if (runningApp != null &&
                windowsUpdateInstaller.spawnUpdater(installer, File(runningApp), expectedSha256)
            ) {
                // The helper relaunches after this process exits; the caller must exit for that to fire.
                logger.info("Spawned Windows updater helper; relaunching after exit")
                return UpdateInstallResult.Relaunching
            }
            logger.warn("Windows self-install unavailable or failed; handing the download to the OS")
        }

        // The download was hash-checked while streaming, but the file has sat on disk since then;
        // re-verify right before it is handed to the OS opener for execution (CWE-367).
        FileSha256.verifyMatch(installer, expectedSha256, "update installer")

        // macOS / Windows, or the Linux fallback when not running as an AppImage: hand the installer
        // to the OS opener, which reveals its folder when it cannot be opened directly.
        return when (open(installer)) {
            OpenFileResult.Opened -> UpdateInstallResult.Opened
            OpenFileResult.Revealed -> UpdateInstallResult.Revealed
            OpenFileResult.Failed -> UpdateInstallResult.Failed
        }
    }
}
