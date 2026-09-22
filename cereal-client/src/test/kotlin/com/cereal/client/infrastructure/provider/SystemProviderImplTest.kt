package com.cereal.client.infrastructure.provider

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.model.exception.UpdateVerificationException
import com.cereal.client.infrastructure.data.FileSha256
import com.cereal.client.infrastructure.data.datasource.os.AppImageInstaller
import com.cereal.client.infrastructure.data.datasource.os.BrowserDataSource
import com.cereal.client.infrastructure.data.datasource.os.MacAppBundleInstaller
import com.cereal.client.infrastructure.data.datasource.os.NotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.WindowsUpdateInstaller
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SystemProviderImplTest {
    private lateinit var applicationConfig: ApplicationConfig
    private lateinit var notificationDataSource: NotificationDataSource
    private lateinit var browserDataSource: BrowserDataSource
    private lateinit var appImageInstaller: AppImageInstaller
    private lateinit var macAppBundleInstaller: MacAppBundleInstaller
    private lateinit var windowsUpdateInstaller: WindowsUpdateInstaller
    private lateinit var repository: SystemProviderImpl

    @BeforeEach
    fun setUp() {
        applicationConfig = mockk(relaxed = true)
        // A real classpath resource (an SVG) that ImageIO cannot decode, so loadImageResource
        // returns null without throwing — mirroring the production icon-load path.
        every { applicationConfig.appIcon } returns "application.svg"
        every { applicationConfig.title } returns "Cereal"
        // Default the host OS to Linux; OS-specific tests build a dedicated repository below.
        every { applicationConfig.operatingSystem } returns OperatingSystemType.Linux
        notificationDataSource = mockk(relaxed = true)
        browserDataSource = mockk(relaxed = true)
        appImageInstaller = mockk(relaxed = true)
        macAppBundleInstaller = mockk(relaxed = true)
        windowsUpdateInstaller = mockk(relaxed = true)
        repository = systemProvider()
    }

    /** Builds a [SystemProviderImpl] against the current mocks; the OS must be stubbed before calling. */
    private fun systemProvider(): SystemProviderImpl =
        SystemProviderImpl(
            applicationConfig,
            notificationDataSource,
            browserDataSource,
            appImageInstaller,
            macAppBundleInstaller,
            windowsUpdateInstaller,
        )

    @Test
    fun `createTrayIcon skips tray creation when image cannot be decoded`() =
        runTest {
            // The SVG resource decodes to a null image, so the tray icon is skipped (no throw).
            repository.createTrayIcon()

            coVerify(exactly = 0) { notificationDataSource.createTrayIcon(any(), any()) }
        }

    @Test
    fun `browser with empty url returns early without touching datasource`() =
        runTest {
            repository.browser("")

            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `browser refuses non-http schemes without touching datasource`() =
        runTest {
            // A marketplace script could set supportUrl to a dangerous scheme; these must never
            // reach xdg-open / Desktop#browse.
            for (url in listOf("file:///etc/passwd", "smb://attacker/share", "javascript:alert(1)", "not a url")) {
                repository.browser(url)
            }

            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `browser opens via desktop browse when supported`() =
        runTest {
            // Pin a non-Linux OS: only there is Desktop#browse the first (and only) attempt. On
            // Linux xdg-open is preferred, so this assertion must not depend on the CI host OS.
            val nonLinuxRepository = nonLinuxRepository()
            every { browserDataSource.attemptDesktopBrowse("https://example.com") } returns true

            nonLinuxRepository.browser("https://example.com")

            coVerify { browserDataSource.attemptDesktopBrowse("https://example.com") }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `browser falls through when desktop browse fails`() =
        runTest {
            every { browserDataSource.attemptDesktopBrowse("https://example.com") } returns false

            repository.browser("https://example.com")

            coVerify { browserDataSource.attemptDesktopBrowse("https://example.com") }
        }

    @Test
    fun `open with empty path returns early without touching datasource`() =
        runTest {
            repository.open(File(""))

            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `open opens via desktop open when supported`() =
        runTest {
            // Pin a non-Linux OS so Desktop#open is the only attempt (xdg-open is preferred on Linux).
            val nonLinuxRepository = nonLinuxRepository()
            val dir = File("/tmp/some-dir")
            every { browserDataSource.attemptDesktopOpen(dir) } returns true

            nonLinuxRepository.open(dir)

            coVerify { browserDataSource.attemptDesktopOpen(dir) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `open falls through and warns when desktop open fails`() =
        runTest {
            val dir = File("/tmp/some-dir")
            every { browserDataSource.attemptDesktopOpen(dir) } returns false

            repository.open(dir)

            coVerify { browserDataSource.attemptDesktopOpen(dir) }
        }

    /**
     * Builds a repository whose `shouldAttemptXdg` flag is true by pretending the host OS is Linux.
     * The flag is computed in the constructor, so the OS must be set before construction.
     */
    private fun linuxRepository(): SystemProviderImpl {
        every { applicationConfig.operatingSystem } returns OperatingSystemType.Linux
        return systemProvider()
    }

    /**
     * Builds a repository whose `shouldAttemptXdg` flag is false by pretending the host OS is macOS,
     * so Desktop is the only open/browse path. Without this the test would depend on the CI host OS.
     */
    private fun nonLinuxRepository(): SystemProviderImpl {
        every { applicationConfig.operatingSystem } returns OperatingSystemType.MacOS
        return systemProvider()
    }

    private fun windowsRepository(): SystemProviderImpl {
        every { applicationConfig.operatingSystem } returns OperatingSystemType.Windows
        return systemProvider()
    }

    @Test
    fun `browser prefers xdg-open on Linux and skips desktop browse when it succeeds`() =
        runTest {
            val linuxRepository = linuxRepository()
            every { browserDataSource.attemptXdgOpen("https://example.com") } returns true

            linuxRepository.browser("https://example.com")

            coVerify { browserDataSource.attemptXdgOpen("https://example.com") }
            // Desktop#browse is unreliable on Linux, so it must not be relied on when xdg-open works.
            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
        }

    @Test
    fun `browser falls back to desktop browse on Linux when xdg-open fails`() =
        runTest {
            val linuxRepository = linuxRepository()
            every { browserDataSource.attemptXdgOpen("https://example.com") } returns false
            every { browserDataSource.attemptDesktopBrowse("https://example.com") } returns true

            linuxRepository.browser("https://example.com")

            coVerify { browserDataSource.attemptXdgOpen("https://example.com") }
            coVerify { browserDataSource.attemptDesktopBrowse("https://example.com") }
        }

    @Test
    fun `open prefers xdg-open on Linux and skips desktop open when it succeeds`() =
        runTest {
            val linuxRepository = linuxRepository()
            val dir = File("/tmp/some-dir")
            every { browserDataSource.attemptXdgOpen(dir.absolutePath) } returns true

            val result = linuxRepository.open(dir)

            assertEquals(OpenFileResult.Opened, result)
            coVerify { browserDataSource.attemptXdgOpen(dir.absolutePath) }
            // Desktop#open is unreliable on Linux, so it must not be relied on when xdg-open works.
            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
        }

    @Test
    fun `open falls back to desktop open on Linux when xdg-open fails`() =
        runTest {
            val linuxRepository = linuxRepository()
            val dir = File("/tmp/some-dir")
            every { browserDataSource.attemptXdgOpen(dir.absolutePath) } returns false
            every { browserDataSource.attemptDesktopOpen(dir) } returns true

            val result = linuxRepository.open(dir)

            assertEquals(OpenFileResult.Opened, result)
            coVerify { browserDataSource.attemptXdgOpen(dir.absolutePath) }
            coVerify { browserDataSource.attemptDesktopOpen(dir) }
        }

    @Test
    fun `open reveals the containing folder on Linux when the file cannot be opened`() =
        runTest {
            val linuxRepository = linuxRepository()
            val file = File("/tmp/downloads/cereal-client-latest.AppImage")
            val parentPath = file.absoluteFile.parentFile!!.absolutePath
            // The file itself cannot be opened (no handler for it)...
            every { browserDataSource.attemptXdgOpen(file.absolutePath) } returns false
            every { browserDataSource.attemptDesktopOpen(file) } returns false
            // ...but its containing folder can be revealed.
            every { browserDataSource.attemptXdgOpen(parentPath) } returns true

            val result = linuxRepository.open(file)

            assertEquals(OpenFileResult.Revealed, result)
            coVerify { browserDataSource.attemptXdgOpen(parentPath) }
        }

    @Test
    fun `open returns Failed on Linux when neither opening nor revealing works`() =
        runTest {
            val linuxRepository = linuxRepository()
            val file = File("/tmp/downloads/cereal-client-latest.AppImage")
            // Relaxed mock returns false for every attemptXdgOpen/attemptDesktopOpen call.

            val result = linuxRepository.open(file)

            assertEquals(OpenFileResult.Failed, result)
        }

    @Test
    fun `open returns Failed for an empty path without touching the datasource`() =
        runTest {
            val result = repository.open(File(""))

            assertEquals(OpenFileResult.Failed, result)
            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `installUpdate self-installs and relaunches when running as a Linux AppImage`() =
        runTest {
            val linuxRepository = linuxRepository()
            val installer = File("/tmp/cereal-client-latest.AppImage")
            every { appImageInstaller.runningAppImagePath() } returns "/home/user/Cereal.AppImage"
            every {
                appImageInstaller.replaceAndRelaunch(installer, File("/home/user/Cereal.AppImage"), null)
            } returns true

            val result = linuxRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Relaunching, result)
            coVerify { appImageInstaller.replaceAndRelaunch(installer, File("/home/user/Cereal.AppImage"), null) }
            // No OS open/reveal when the self-install succeeded.
            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
            coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
        }

    @Test
    fun `installUpdate hands the download to the OS when the AppImage self-install fails`() =
        runTest {
            val linuxRepository = linuxRepository()
            val installer = File("/tmp/sub/cereal-client-latest.AppImage")
            every { appImageInstaller.runningAppImagePath() } returns "/home/user/Cereal.AppImage"
            every { appImageInstaller.replaceAndRelaunch(any(), any(), any()) } returns false
            // Falls back to open(installer); the OS opener succeeds via xdg-open.
            every { browserDataSource.attemptXdgOpen(installer.absolutePath) } returns true

            val result = linuxRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Opened, result)
            coVerify { browserDataSource.attemptXdgOpen(installer.absolutePath) }
        }

    @Test
    fun `installUpdate reveals the download folder on Linux when not running as an AppImage`() =
        runTest {
            val linuxRepository = linuxRepository()
            val installer = File("/tmp/sub/cereal-client-latest.AppImage")
            val parentPath = installer.absoluteFile.parentFile!!.absolutePath
            every { appImageInstaller.runningAppImagePath() } returns null
            // The AppImage itself can't be opened, but its folder is revealed.
            every { browserDataSource.attemptXdgOpen(installer.absolutePath) } returns false
            every { browserDataSource.attemptDesktopOpen(installer) } returns false
            every { browserDataSource.attemptXdgOpen(parentPath) } returns true

            val result = linuxRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Revealed, result)
            coVerify(exactly = 0) { appImageInstaller.replaceAndRelaunch(any(), any(), any()) }
            coVerify { browserDataSource.attemptXdgOpen(parentPath) }
        }

    @Test
    fun `installUpdate opens the installer directly on non-Linux platforms`() =
        runTest {
            val macRepository = nonLinuxRepository()
            val installer = File("/tmp/cereal-client-latest.dmg")
            every { browserDataSource.attemptDesktopOpen(installer) } returns true

            val result = macRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Opened, result)
            // Opens the installer itself (not the folder); never touches the AppImage installer.
            coVerify { browserDataSource.attemptDesktopOpen(installer) }
            coVerify(exactly = 0) { appImageInstaller.runningAppImagePath() }
            coVerify(exactly = 0) { appImageInstaller.replaceAndRelaunch(any(), any(), any()) }
        }

    @Test
    fun `installUpdate opens the installer when it still matches the expected sha`(
        @TempDir tmp: Path,
    ) = runTest {
        val macRepository = nonLinuxRepository()
        val installer = File(tmp.toFile(), "cereal-client-latest.dmg").apply { writeText("INSTALLER") }
        every { browserDataSource.attemptDesktopOpen(installer) } returns true

        val result = macRepository.installUpdate(installer, FileSha256.hash(installer))

        assertEquals(UpdateInstallResult.Opened, result)
        coVerify { browserDataSource.attemptDesktopOpen(installer) }
    }

    @Test
    fun `installUpdate aborts before handing a tampered installer to the OS`(
        @TempDir tmp: Path,
    ) = runTest {
        val macRepository = nonLinuxRepository()
        // The file on disk no longer matches the digest checked during download — the CWE-367
        // swap window this re-verification closes.
        val installer = File(tmp.toFile(), "cereal-client-latest.dmg").apply { writeText("TAMPERED") }
        val expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        assertThrows<UpdateVerificationException> {
            macRepository.installUpdate(installer, expectedSha256)
        }

        // The tampered installer must never reach the OS opener.
        coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
        coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
    }

    @Test
    fun `installUpdate re-verifies before the Linux OS-opener fallback as well`(
        @TempDir tmp: Path,
    ) = runTest {
        val linuxRepository = linuxRepository()
        val installer = File(tmp.toFile(), "cereal-client-latest.AppImage").apply { writeText("TAMPERED") }
        val expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        every { appImageInstaller.runningAppImagePath() } returns null

        assertThrows<UpdateVerificationException> {
            linuxRepository.installUpdate(installer, expectedSha256)
        }

        coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
        coVerify(exactly = 0) { browserDataSource.attemptXdgOpen(any()) }
    }

    @Test
    fun `installUpdate passes the expected sha to the AppImage self-install`(
        @TempDir tmp: Path,
    ) = runTest {
        val linuxRepository = linuxRepository()
        val installer = File(tmp.toFile(), "cereal-client-latest.AppImage").apply { writeText("NEW") }
        val expectedSha256 = FileSha256.hash(installer)
        every { appImageInstaller.runningAppImagePath() } returns "/home/user/Cereal.AppImage"
        every {
            appImageInstaller.replaceAndRelaunch(installer, File("/home/user/Cereal.AppImage"), expectedSha256)
        } returns true

        val result = linuxRepository.installUpdate(installer, expectedSha256)

        assertEquals(UpdateInstallResult.Relaunching, result)
        coVerify {
            appImageInstaller.replaceAndRelaunch(installer, File("/home/user/Cereal.AppImage"), expectedSha256)
        }
    }

    @Test
    fun `installUpdate swaps the app bundle and relaunches when running as an installed macOS app`() =
        runTest {
            val macRepository = nonLinuxRepository()
            val installer = File("/tmp/cereal-client-latest.zip")
            every { macAppBundleInstaller.runningAppBundlePath() } returns "/Applications/Cereal.app"
            every {
                macAppBundleInstaller.replaceAndRelaunch(installer, File("/Applications/Cereal.app"), null)
            } returns true

            val result = macRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Relaunching, result)
            coVerify { macAppBundleInstaller.replaceAndRelaunch(installer, File("/Applications/Cereal.app"), null) }
            // No OS open/reveal when the self-install succeeded.
            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
        }

    @Test
    fun `installUpdate hands the download to the OS when the macOS bundle swap fails`() =
        runTest {
            val macRepository = nonLinuxRepository()
            val installer = File("/tmp/cereal-client-latest.zip")
            every { macAppBundleInstaller.runningAppBundlePath() } returns "/Applications/Cereal.app"
            every { macAppBundleInstaller.replaceAndRelaunch(any(), any(), any()) } returns false
            every { browserDataSource.attemptDesktopOpen(installer) } returns true

            val result = macRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Opened, result)
            coVerify { browserDataSource.attemptDesktopOpen(installer) }
        }

    @Test
    fun `installUpdate reveals the download on macOS when not running as an installed bundle`() =
        runTest {
            val macRepository = nonLinuxRepository()
            val installer = File("/tmp/sub/cereal-client-latest.zip")
            val parentPath = installer.absoluteFile.parentFile!!.absolutePath
            every { macAppBundleInstaller.runningAppBundlePath() } returns null
            every { browserDataSource.attemptDesktopOpen(installer) } returns false
            every { browserDataSource.attemptDesktopOpen(File(parentPath)) } returns true

            val result = macRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Revealed, result)
            coVerify(exactly = 0) { macAppBundleInstaller.replaceAndRelaunch(any(), any(), any()) }
        }

    @Test
    fun `installUpdate spawns the updater helper and relaunches when running as an installed Windows app`() =
        runTest {
            val windowsRepository = windowsRepository()
            val installer = File("/tmp/cereal-client-latest.exe")
            every { windowsUpdateInstaller.runningAppPath() } returns "C:/Users/x/AppData/Local/Cereal/Cereal.exe"
            every {
                windowsUpdateInstaller.spawnUpdater(installer, File("C:/Users/x/AppData/Local/Cereal/Cereal.exe"), null)
            } returns true

            val result = windowsRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Relaunching, result)
            coVerify {
                windowsUpdateInstaller.spawnUpdater(installer, File("C:/Users/x/AppData/Local/Cereal/Cereal.exe"), null)
            }
            coVerify(exactly = 0) { browserDataSource.attemptDesktopOpen(any()) }
        }

    @Test
    fun `installUpdate hands the download to the OS when the Windows helper cannot be spawned`() =
        runTest {
            val windowsRepository = windowsRepository()
            val installer = File("/tmp/cereal-client-latest.exe")
            // Per-machine install / dev run: no self-updatable path detected.
            every { windowsUpdateInstaller.runningAppPath() } returns null
            every { browserDataSource.attemptDesktopOpen(installer) } returns true

            val result = windowsRepository.installUpdate(installer, expectedSha256 = null)

            assertEquals(UpdateInstallResult.Opened, result)
            coVerify(exactly = 0) { windowsUpdateInstaller.spawnUpdater(any(), any(), any()) }
            coVerify { browserDataSource.attemptDesktopOpen(installer) }
        }
}
