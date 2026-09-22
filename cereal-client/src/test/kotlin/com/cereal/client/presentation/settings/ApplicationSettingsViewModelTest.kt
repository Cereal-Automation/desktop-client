package com.cereal.client.presentation.settings

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.app.DownloadLatestAppVersionInteractor
import com.cereal.client.application.interactor.app.InstallUpdateInteractor
import com.cereal.client.application.interactor.settings.GetApplicationPreferencesSettingsInteractor
import com.cereal.client.application.interactor.settings.OpenFileInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.SetDevelopmentScriptsInteractor
import com.cereal.client.application.interactor.settings.developers.SetShowDebugLogsInteractor
import com.cereal.client.application.interactor.settings.discord.SetDiscordActivityStatusEnabledInteractor
import com.cereal.client.application.interactor.settings.notifications.SaveAllNotificationSettingsInteractor
import com.cereal.client.application.interactor.settings.notifications.SendNotificationTestMessageInteractor
import com.cereal.client.application.interactor.settings.privacy.GetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.privacy.SetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.proxy.SetProxyHealthCheckIntervalInteractor
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCrashReportingProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.feedback.FeedbackAction
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationSettingsViewModelTest {
    private lateinit var viewModel: ApplicationSettingsViewModel
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    private val getApplicationPreferencesSettingsInteractor: GetApplicationPreferencesSettingsInteractor = mockk(relaxed = true)
    private val openUrlInteractor: OpenUrlInteractor = mockk(relaxed = true)
    private val openFileInteractor: OpenFileInteractor = mockk(relaxed = true)
    private val setDevelopmentScriptsInteractor: SetDevelopmentScriptsInteractor = mockk(relaxed = true)
    private val setShowDebugLogsInteractor: SetShowDebugLogsInteractor = mockk(relaxed = true)
    private val setProxyHealthCheckIntervalInteractor: SetProxyHealthCheckIntervalInteractor = mockk(relaxed = true)
    private val setDiscordActivityStatusEnabledInteractor: SetDiscordActivityStatusEnabledInteractor = mockk(relaxed = true)

    // Real interactors over an in-memory provider: the crash-reporting toggle is worth asserting as
    // behaviour (the stored choice changes), which a relaxed mock would hide.
    private val crashReportingProvider = InMemoryCrashReportingProvider()
    private val getCrashReportingEnabledInteractor = GetCrashReportingEnabledInteractor(crashReportingProvider)
    private val setCrashReportingEnabledInteractor = SetCrashReportingEnabledInteractor(crashReportingProvider)
    private val sendNotificationTestMessageInteractor: SendNotificationTestMessageInteractor = mockk(relaxed = true)
    private val saveAllNotificationSettingsInteractor: SaveAllNotificationSettingsInteractor = mockk(relaxed = true)
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor = mockk(relaxed = true)
    private val downloadLatestAppVersionInteractor: DownloadLatestAppVersionInteractor = mockk(relaxed = true)
    private val installUpdateInteractor: InstallUpdateInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)

        // Init reads from these — keep them empty by default so init is a no-op.
        coEvery { getApplicationPreferencesSettingsInteractor(any()) } returns flowOf()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            ApplicationSettingsViewModel(
                scope = CoroutineScope(dispatcher),
                dispatcherProvider = dispatcherProvider,
                getApplicationPreferencesSettingsInteractor = getApplicationPreferencesSettingsInteractor,
                openUrlInteractor = openUrlInteractor,
                openFileInteractor = openFileInteractor,
                setDevelopmentScriptsInteractor = setDevelopmentScriptsInteractor,
                setShowDebugLogsInteractor = setShowDebugLogsInteractor,
                setProxyHealthCheckIntervalInteractor = setProxyHealthCheckIntervalInteractor,
                setDiscordActivityStatusEnabledInteractor = setDiscordActivityStatusEnabledInteractor,
                getCrashReportingEnabledInteractor = getCrashReportingEnabledInteractor,
                setCrashReportingEnabledInteractor = setCrashReportingEnabledInteractor,
                sendNotificationTestMessageInteractor = sendNotificationTestMessageInteractor,
                saveAllNotificationSettingsInteractor = saveAllNotificationSettingsInteractor,
                checkForUpdatesInteractor = checkForUpdatesInteractor,
                downloadLatestAppVersionInteractor = downloadLatestAppVersionInteractor,
                installUpdateInteractor = installUpdateInteractor,
                errorResolver = errorResolver,
            )
    }

    private fun sampleSettings() =
        ApplicationPreferenceSettings(
            desktopNotificationsEnabled = false,
            developmentScriptsEnabled = true,
            discordActivityStatusEnabled = false,
            discordWebhookEnabled = true,
            discordWebhookUrl = "https://discord.com/api/webhooks/123",
            telegramEnabled = true,
            telegramBotToken = "bot-token",
            telegramChatId = "chat-id",
            emailEnabled = true,
            emailSmtpHost = "smtp.example.com",
            emailSmtpPort = 25,
            emailUsername = "user",
            emailPassword = "pass",
            emailFrom = "from@example.com",
            emailTo = "to@example.com",
            emailUseTls = false,
            showDebugLogs = true,
            proxyHealthCheckInterval = ProxyHealthCheckInterval.EVERY_6_HOURS,
        )

    private fun version(major: Int): Version =
        Version(
            version = SemVer(major, 0, 0),
            minRequiredVersion = SemVer(1, 0, 0),
            downloadUrl = "https://example.com/download",
        )

    @Test
    fun `init maps loaded settings onto state`() =
        runTest {
            coEvery { getApplicationPreferencesSettingsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(sampleSettings()))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.desktopNotificationsEnabled.value)
            assertTrue(viewModel.developmentScriptsEnabled.value)
            assertTrue(viewModel.showDebugLogsEnabled.value)
            assertEquals(ProxyHealthCheckInterval.EVERY_6_HOURS, viewModel.proxyHealthCheckInterval.value)
            assertFalse(viewModel.discordActivityStatusEnabled.value)
            assertTrue(viewModel.discordWebhookEnabledState.value)
            assertEquals("https://discord.com/api/webhooks/123", viewModel.discordWebhookUrlState.text)
            assertTrue(viewModel.telegramEnabled.value)
            assertEquals("bot-token", viewModel.telegramBotTokenState.text)
            assertEquals("chat-id", viewModel.telegramChatIdState.text)
            assertTrue(viewModel.emailEnabled.value)
            assertEquals("smtp.example.com", viewModel.emailSmtpHostState.text)
            assertEquals("25", viewModel.emailSmtpPortState.text)
            assertEquals("user", viewModel.emailUsernameState.text)
            assertEquals("to@example.com", viewModel.emailToState.text)
            assertFalse(viewModel.emailUseTls.value)
        }

    @Test
    fun `setDesktopNotificationsEnabled updates state synchronously`() {
        createViewModel()

        viewModel.setDesktopNotificationsEnabled(false)

        assertFalse(viewModel.desktopNotificationsEnabled.value)
    }

    @Test
    fun `setDiscordWebhookEnabled updates state synchronously`() {
        createViewModel()

        viewModel.setDiscordWebhookEnabled(true)

        assertTrue(viewModel.discordWebhookEnabledState.value)
    }

    @Test
    fun `setTelegramEnabled and setEmailEnabled and setEmailUseTls update state synchronously`() {
        createViewModel()

        viewModel.setTelegramEnabled(true)
        viewModel.setEmailEnabled(true)
        viewModel.setEmailUseTls(false)

        assertTrue(viewModel.telegramEnabled.value)
        assertTrue(viewModel.emailEnabled.value)
        assertFalse(viewModel.emailUseTls.value)
    }

    @Test
    fun `setDevelopmentScriptsEnabled toggles updating flag and invokes interactor`() =
        runTest {
            coEvery { setDevelopmentScriptsInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(Unit))
            }
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setDevelopmentScriptsEnabled(true)
            // Flag is set synchronously before the coroutine runs.
            assertTrue(viewModel.isDevelopmentScriptsUpdating.value)

            dispatcher.scheduler.advanceUntilIdle()

            val params = slot<SetDevelopmentScriptsInteractor.Params>()
            coVerify(exactly = 1) { setDevelopmentScriptsInteractor(capture(params), any()) }
            assertTrue(params.captured.enabled)
            assertFalse(viewModel.isDevelopmentScriptsUpdating.value)
        }

    @Test
    fun `setShowDebugLogs invokes interactor with given value`() =
        runTest {
            createViewModel()

            viewModel.setShowDebugLogs(true)
            dispatcher.scheduler.advanceUntilIdle()

            val params = slot<SetShowDebugLogsInteractor.Params>()
            coVerify(exactly = 1) { setShowDebugLogsInteractor(capture(params), any()) }
            assertTrue(params.captured.enabled)
        }

    @Test
    fun `setProxyHealthCheckInterval invokes interactor with given interval`() =
        runTest {
            createViewModel()

            viewModel.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_24_HOURS)
            dispatcher.scheduler.advanceUntilIdle()

            val params = slot<SetProxyHealthCheckIntervalInteractor.Params>()
            coVerify(exactly = 1) { setProxyHealthCheckIntervalInteractor(capture(params), any()) }
            assertEquals(ProxyHealthCheckInterval.EVERY_24_HOURS, params.captured.interval)
        }

    @Test
    fun `setDiscordActivityStatusEnabled invokes interactor`() =
        runTest {
            createViewModel()

            viewModel.setDiscordActivityStatusEnabled(false)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { setDiscordActivityStatusEnabledInteractor(any(), any()) }
        }

    @Test
    fun `testDiscordWebhook shows success feedback when interactor succeeds`() =
        runTest {
            coEvery { sendNotificationTestMessageInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(Unit))
            }
            createViewModel()

            viewModel.testDiscordWebhook()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<FeedbackAction.Success>(viewModel.feedbackAction.value)
        }

    @Test
    fun `testTelegramMessage shows error feedback when interactor fails`() =
        runTest {
            coEvery { sendNotificationTestMessageInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
                onResult(SuspendableResult.Failure(Exception("nope")))
            }
            createViewModel()

            viewModel.testTelegramMessage()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<FeedbackAction.Error>(viewModel.feedbackAction.value)
        }

    @Test
    fun `openUrl invokes interactor with given url`() =
        runTest {
            createViewModel()

            viewModel.openUrl("https://example.com")
            dispatcher.scheduler.advanceUntilIdle()

            val params = slot<OpenUrlInteractor.Params>()
            coVerify(exactly = 1) { openUrlInteractor(capture(params), any()) }
            assertEquals("https://example.com", params.captured.url)
        }

    @Test
    fun `saveAllNotifications shows success feedback and invokes interactor`() =
        runTest {
            coEvery { saveAllNotificationSettingsInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(Unit))
            }
            createViewModel()

            viewModel.saveAllNotifications()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { saveAllNotificationSettingsInteractor(any(), any()) }
            assertIs<FeedbackAction.Success>(viewModel.feedbackAction.value)
        }

    @Test
    fun `checkForUpdates shows up-to-date feedback when no update is available`() =
        runTest {
            coEvery { checkForUpdatesInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<UpdateCheckResult, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(UpdateCheckResult.UpToDate))
            }
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.checkForUpdates()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.isCheckingForUpdates.value)
            assertIs<FeedbackAction.Success>(viewModel.feedbackAction.value)
            assertNull(viewModel.updateAvailableVersion.value)
        }

    @Test
    fun `checkForUpdates surfaces available version when update is available`() =
        runTest {
            val available = version(2)
            coEvery { checkForUpdatesInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<UpdateCheckResult, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(UpdateCheckResult.UpdateAvailable(available)))
            }
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.checkForUpdates()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(available, viewModel.updateAvailableVersion.value)
        }

    @Test
    fun `checkForUpdates surfaces required version when update is required`() =
        runTest {
            val required = version(2)
            coEvery { checkForUpdatesInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<UpdateCheckResult, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(UpdateCheckResult.UpdateRequired(required)))
            }
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.checkForUpdates()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(required, viewModel.updateRequiredVersion.value)
        }

    @Test
    fun `dismissUpdateDialogs clears both available and required versions`() {
        createViewModel()

        viewModel.dismissUpdateDialogs()

        assertNull(viewModel.updateAvailableVersion.value)
        assertNull(viewModel.updateRequiredVersion.value)
    }

    @Test
    fun `dismissDownloadDialog resets download state`() {
        createViewModel()

        viewModel.dismissDownloadDialog()

        assertNull(viewModel.updateDownloadProgress.value)
        assertEquals("", viewModel.updateDownloadStatus.value)
        assertNull(viewModel.openInstaller.value)
    }

    @Test
    fun `testEmailMessage shows success feedback when interactor succeeds`() =
        runTest {
            coEvery { sendNotificationTestMessageInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
            }
            createViewModel()

            viewModel.testEmailMessage()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<FeedbackAction.Success>(viewModel.feedbackAction.value)
        }

    @Test
    fun `testEmailMessage shows error feedback when interactor fails`() =
        runTest {
            coEvery { sendNotificationTestMessageInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Failure(Exception("nope")))
            }
            createViewModel()

            viewModel.testEmailMessage()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<FeedbackAction.Error>(viewModel.feedbackAction.value)
        }

    @Test
    fun `openPath invokes the open file interactor`() =
        runTest {
            createViewModel()

            viewModel.openPath(java.io.File("/tmp/x"))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { openFileInteractor(any(), any()) }
        }

    @Test
    fun `openInstallerFile invokes the install update interactor and stays running when opened`() =
        runTest {
            // Opened (the OS launched the installer on macOS/Windows) must not exit the process.
            coEvery { installUpdateInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<UpdateInstallResult, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(UpdateInstallResult.Opened))
            }
            createViewModel()

            viewModel.openInstallerFile(java.io.File("/tmp/installer"))
            dispatcher.scheduler.advanceUntilIdle()

            val params = slot<InstallUpdateInteractor.Params>()
            coVerify(exactly = 1) { installUpdateInteractor(capture(params), any()) }
            assertEquals(java.io.File("/tmp/installer"), params.captured.installer)
        }

    @Test
    fun `dismissUpdateAvailableDialog clears the available version`() {
        createViewModel()

        viewModel.dismissUpdateAvailableDialog()

        assertNull(viewModel.updateAvailableVersion.value)
    }

    @Test
    fun `onConfirmUpdate downloads and exposes the installer for direct builds`() =
        runTest {
            coEvery { downloadLatestAppVersionInteractor(any()) } returns
                flowOf(
                    SuspendableResult.Success(DownloadStatus.Downloading(50)),
                    SuspendableResult.Success(DownloadStatus.Finished(java.io.File("/tmp/installer"))),
                )
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onConfirmUpdate(version(2))
            dispatcher.scheduler.advanceUntilIdle()

            // Store builds short-circuit to a URL; direct builds download to completion.
            if (com.cereal_automation.cereal_client.BuildConfig.IS_STORE_BUILD) {
                assertNull(viewModel.openInstaller.value)
            } else {
                assertEquals(1f, viewModel.updateDownloadProgress.value)
                assertEquals("Finished", viewModel.updateDownloadStatus.value)
                assertNotNull(viewModel.openInstaller.value)
            }
        }

    @Test
    fun `openInstallerFile forwards the downloaded installer's expected sha`() =
        runTest {
            // Store builds never download, so there is no digest to forward there.
            org.junit.jupiter.api.Assumptions
                .assumeFalse(com.cereal_automation.cereal_client.BuildConfig.IS_STORE_BUILD)
            val installer = java.io.File("/tmp/installer")
            val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            coEvery { downloadLatestAppVersionInteractor(any()) } returns
                flowOf(SuspendableResult.Success(DownloadStatus.Finished(installer, sha256)))
            coEvery { installUpdateInteractor(any(), any()) } coAnswers {
                val onResult = secondArg<suspend (SuspendableResult<UpdateInstallResult, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(UpdateInstallResult.Opened))
            }
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onConfirmUpdate(version(2))
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.openInstallerFile(installer)
            dispatcher.scheduler.advanceUntilIdle()

            // The digest from the release metadata must travel with the file so the installer is
            // re-verified right before launch.
            val params = slot<InstallUpdateInteractor.Params>()
            coVerify(exactly = 1) { installUpdateInteractor(capture(params), any()) }
            assertEquals(sha256, params.captured.expectedSha256)
        }

    @Test
    fun `crash reporting starts out enabled and the toggle flips the stored choice`() =
        runTest {
            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.crashReportingEnabled.value, "crash reporting is opt-out, not opt-in")

            viewModel.setCrashReportingEnabled(false)
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.crashReportingEnabled.value)
            assertFalse(crashReportingProvider.isEnabled(), "the choice has to reach the provider, not just the UI")
        }

    @Test
    fun `a recorded opt-out is what the settings screen shows`() =
        runTest {
            crashReportingProvider.setEnabled(false)

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.crashReportingEnabled.value)
        }
}
