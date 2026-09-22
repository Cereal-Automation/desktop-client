package com.cereal.client.presentation.settings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
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
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.feedback.FeedbackAction
import com.cereal.client.presentation.settings.state.DiscordWebhookUrlState
import com.cereal.client.presentation.settings.state.EmailFromState
import com.cereal.client.presentation.settings.state.EmailPasswordState
import com.cereal.client.presentation.settings.state.EmailSmtpHostState
import com.cereal.client.presentation.settings.state.EmailSmtpPortState
import com.cereal.client.presentation.settings.state.EmailToState
import com.cereal.client.presentation.settings.state.EmailUsernameState
import com.cereal.client.presentation.settings.state.TelegramBotTokenState
import com.cereal.client.presentation.settings.state.TelegramChatIdState
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal_automation.cereal_client.BuildConfig
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

// Aggregates many settings actions (exceeding the function threshold); its constructor
// parameters are injected dependencies (interactors and the error resolver).
@Suppress("TooManyFunctions", "LongParameterList")
class ApplicationSettingsViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getApplicationPreferencesSettingsInteractor: GetApplicationPreferencesSettingsInteractor,
    private val openUrlInteractor: OpenUrlInteractor,
    private val openFileInteractor: OpenFileInteractor,
    private val setDevelopmentScriptsInteractor: SetDevelopmentScriptsInteractor,
    private val setShowDebugLogsInteractor: SetShowDebugLogsInteractor,
    private val setProxyHealthCheckIntervalInteractor: SetProxyHealthCheckIntervalInteractor,
    private val setDiscordActivityStatusEnabledInteractor: SetDiscordActivityStatusEnabledInteractor,
    private val getCrashReportingEnabledInteractor: GetCrashReportingEnabledInteractor,
    private val setCrashReportingEnabledInteractor: SetCrashReportingEnabledInteractor,
    private val sendNotificationTestMessageInteractor: SendNotificationTestMessageInteractor,
    private val saveAllNotificationSettingsInteractor: SaveAllNotificationSettingsInteractor,
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor,
    private val downloadLatestAppVersionInteractor: DownloadLatestAppVersionInteractor,
    private val installUpdateInteractor: InstallUpdateInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val _desktopNotificationsEnabled = mutableStateOf(true)
    val desktopNotificationsEnabled: State<Boolean> = _desktopNotificationsEnabled

    private val _developmentScriptsEnabled = mutableStateOf(false)
    val developmentScriptsEnabled: State<Boolean> = _developmentScriptsEnabled

    private val _isDevelopmentScriptsUpdating = mutableStateOf(false)
    val isDevelopmentScriptsUpdating: State<Boolean> = _isDevelopmentScriptsUpdating

    private val _showDebugLogsEnabled = mutableStateOf(false)
    val showDebugLogsEnabled: State<Boolean> = _showDebugLogsEnabled

    private val _proxyHealthCheckInterval = mutableStateOf(ProxyHealthCheckInterval.Default)
    val proxyHealthCheckInterval: State<ProxyHealthCheckInterval> = _proxyHealthCheckInterval

    private val _discordActivityStatusEnabled = mutableStateOf(true)
    val discordActivityStatusEnabled: State<Boolean> = _discordActivityStatusEnabled

    // Not part of ApplicationPreferenceSettings: crash reporting lives in the pre-DI bootstrap
    // store, so it is read once here rather than arriving on the settings Flow.
    private val _crashReportingEnabled = mutableStateOf(true)
    val crashReportingEnabled: State<Boolean> = _crashReportingEnabled

    private val _discordWebhookEnabledState = mutableStateOf(false)
    val discordWebhookEnabledState: State<Boolean> = _discordWebhookEnabledState

    val discordWebhookUrlState = DiscordWebhookUrlState()

    private val _telegramEnabled = mutableStateOf(false)
    val telegramEnabled: State<Boolean> = _telegramEnabled

    val telegramBotTokenState = TelegramBotTokenState()
    val telegramChatIdState = TelegramChatIdState()

    private val _emailEnabled = mutableStateOf(false)
    val emailEnabled: State<Boolean> = _emailEnabled

    val emailSmtpHostState = EmailSmtpHostState()
    val emailSmtpPortState = EmailSmtpPortState()
    val emailUsernameState = EmailUsernameState()
    val emailPasswordState = EmailPasswordState()
    val emailFromState = EmailFromState()
    val emailToState = EmailToState()

    // Passed as MutableState to NotificationChannelFields composable — kept mutable intentionally
    val emailUseTls = mutableStateOf(true)

    private val _isCheckingForUpdates = mutableStateOf(false)
    val isCheckingForUpdates: State<Boolean> = _isCheckingForUpdates

    private val _updateAvailableIndicator = mutableStateOf(false)
    val updateAvailableIndicator: State<Boolean> = _updateAvailableIndicator

    private val _updateVersionString = mutableStateOf<String?>(null)
    val updateVersionString: State<String?> = _updateVersionString

    // Shown when an update is available — holds the update version for the dialog
    private val _updateAvailableVersion = mutableStateOf<Version?>(null)
    val updateAvailableVersion: State<Version?> = _updateAvailableVersion

    private val _updateRequiredVersion = mutableStateOf<Version?>(null)
    val updateRequiredVersion: State<Version?> = _updateRequiredVersion

    // Download progress state (null = no download in progress)
    private val _updateDownloadProgress = mutableStateOf<Float?>(null)
    val updateDownloadProgress: State<Float?> = _updateDownloadProgress

    private val _updateDownloadStatus = mutableStateOf("")
    val updateDownloadStatus: State<String> = _updateDownloadStatus

    private val _openInstaller = mutableStateOf<File?>(null)
    val openInstaller: State<File?> = _openInstaller

    /**
     * Expected SHA-256 of the downloaded installer (from the release metadata), kept alongside
     * [openInstaller] so [openInstallerFile] can have it re-verified right before launch.
     */
    private var pendingInstallerSha256: String? = null

    private val _feedbackAction = mutableStateOf<FeedbackAction>(FeedbackAction.None)
    val feedbackAction: State<FeedbackAction> = _feedbackAction

    val errorAction: State<ErrorAction> = errorResolver.errorAction

    private var downloadJob: Job? = null

    init {
        getApplicationSettings()
        getCrashReportingEnabled()
        scope.launch(dispatcherProvider.io) {
            checkForUpdatesInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) {
                    val updateResult = result.value
                    withContext(dispatcherProvider.main) {
                        _updateAvailableIndicator.value = updateResult is UpdateCheckResult.UpdateAvailable ||
                            updateResult is UpdateCheckResult.UpdateRequired

                        _updateVersionString.value =
                            when (updateResult) {
                                is UpdateCheckResult.UpdateAvailable -> updateResult.version.version.toString()
                                is UpdateCheckResult.UpdateRequired -> updateResult.version.version.toString()
                                else -> null
                            }
                    }
                }
            }
        }
    }

    private fun getApplicationSettings() {
        scope.launch(dispatcherProvider.io) {
            getApplicationPreferencesSettingsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { applicationSettings ->
                        updateApplicationSettings(applicationSettings)
                    }
                }
            }
        }
    }

    private fun getCrashReportingEnabled() {
        interactorRunner.launch(getCrashReportingEnabledInteractor, Interactor.None()) { enabled ->
            _crashReportingEnabled.value = enabled
        }
    }

    private fun updateApplicationSettings(applicationSettings: ApplicationPreferenceSettings) {
        _desktopNotificationsEnabled.value = applicationSettings.desktopNotificationsEnabled
        _developmentScriptsEnabled.value = applicationSettings.developmentScriptsEnabled
        _showDebugLogsEnabled.value = applicationSettings.showDebugLogs
        _proxyHealthCheckInterval.value = applicationSettings.proxyHealthCheckInterval
        _discordActivityStatusEnabled.value = applicationSettings.discordActivityStatusEnabled
        _discordWebhookEnabledState.value = applicationSettings.discordWebhookEnabled
        discordWebhookUrlState.text = applicationSettings.discordWebhookUrl.orEmpty()
        _telegramEnabled.value = applicationSettings.telegramEnabled
        telegramBotTokenState.text = applicationSettings.telegramBotToken.orEmpty()
        telegramChatIdState.text = applicationSettings.telegramChatId.orEmpty()
        _emailEnabled.value = applicationSettings.emailEnabled
        emailSmtpHostState.text = applicationSettings.emailSmtpHost.orEmpty()
        emailSmtpPortState.text = applicationSettings.emailSmtpPort.toString()
        emailUsernameState.text = applicationSettings.emailUsername.orEmpty()
        emailPasswordState.text = applicationSettings.emailPassword.orEmpty()
        emailFromState.text = applicationSettings.emailFrom.orEmpty()
        emailToState.text = applicationSettings.emailTo.orEmpty()
        emailUseTls.value = applicationSettings.emailUseTls
    }

    fun setDesktopNotificationsEnabled(enabled: Boolean) {
        _desktopNotificationsEnabled.value = enabled
    }

    fun setDevelopmentScriptsEnabled(enabled: Boolean) {
        _isDevelopmentScriptsUpdating.value = true

        scope.launch(dispatcherProvider.io) {
            val params =
                SetDevelopmentScriptsInteractor.Params(
                    enabled = enabled,
                )
            setDevelopmentScriptsInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    _isDevelopmentScriptsUpdating.value = false

                    result.handleFailureOrElse(errorResolver) {
                        // No-op, setting is updated through Flow.
                    }
                }
            }
        }
    }

    fun setDiscordActivityStatusEnabled(enabled: Boolean) {
        val params = SetDiscordActivityStatusEnabledInteractor.Params(enabled)
        interactorRunner.launch(setDiscordActivityStatusEnabledInteractor, params) {
            // no op
        }
    }

    /**
     * Applies the choice immediately — the interactor shuts the reporting client down (or starts it
     * back up) rather than only recording the flag, so no restart is needed. The state is set
     * optimistically because nothing else publishes it.
     */
    fun setCrashReportingEnabled(enabled: Boolean) {
        _crashReportingEnabled.value = enabled
        val params = SetCrashReportingEnabledInteractor.Params(enabled)
        interactorRunner.launch(setCrashReportingEnabledInteractor, params) {
            // No-op, the state above is the only publisher.
        }
    }

    fun setShowDebugLogs(enabled: Boolean) {
        val params = SetShowDebugLogsInteractor.Params(enabled)
        interactorRunner.launch(setShowDebugLogsInteractor, params) {
            // No-op, setting is updated through Flow.
        }
    }

    fun setProxyHealthCheckInterval(interval: ProxyHealthCheckInterval) {
        val params = SetProxyHealthCheckIntervalInteractor.Params(interval)
        interactorRunner.launch(setProxyHealthCheckIntervalInteractor, params) {
            // No-op, setting is updated through Flow.
        }
    }

    fun setDiscordWebhookEnabled(enabled: Boolean) {
        _discordWebhookEnabledState.value = enabled
    }

    private fun setSuccessFeedback(message: String) {
        _feedbackAction.value =
            FeedbackAction.Success(message) {
                _feedbackAction.value = FeedbackAction.None
            }
    }

    private fun setErrorFeedback(message: String) {
        _feedbackAction.value =
            FeedbackAction.Error(message) {
                _feedbackAction.value = FeedbackAction.None
            }
    }

    fun testDiscordWebhook() {
        scope.launch(dispatcherProvider.io) {
            val params = SendNotificationTestMessageInteractor.Params.Discord(discordWebhookUrlState.text)
            sendNotificationTestMessageInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Success -> {
                            setSuccessFeedback(FEEDBACK_TEST_NOTIFICATION_SUCCESS)
                        }

                        is SuspendableResult.Failure -> {
                            setErrorFeedback(FEEDBACK_TEST_NOTIFICATION_FAILURE)
                        }
                    }
                }
            }
        }
    }

    fun openUrl(url: String) {
        val params = OpenUrlInteractor.Params(url)

        interactorRunner.launch(openUrlInteractor, params) {
        }
    }

    fun openPath(path: File) {
        val params = OpenFileInteractor.Params(path)

        interactorRunner.launch(openFileInteractor, params) {
        }
    }

    fun setTelegramEnabled(enabled: Boolean) {
        _telegramEnabled.value = enabled
    }

    fun setEmailEnabled(enabled: Boolean) {
        _emailEnabled.value = enabled
    }

    fun setEmailUseTls(useTls: Boolean) {
        emailUseTls.value = useTls
    }

    fun testTelegramMessage() {
        scope.launch(dispatcherProvider.io) {
            val params =
                SendNotificationTestMessageInteractor.Params.Telegram(
                    botToken = telegramBotTokenState.text,
                    chatId = telegramChatIdState.text,
                )
            sendNotificationTestMessageInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    when {
                        result is SuspendableResult.Success -> {
                            setSuccessFeedback(FEEDBACK_TEST_NOTIFICATION_SUCCESS)
                        }

                        result is SuspendableResult.Failure -> {
                            setErrorFeedback(FEEDBACK_TEST_NOTIFICATION_FAILURE)
                        }
                    }
                }
            }
        }
    }

    fun testEmailMessage() {
        scope.launch(dispatcherProvider.io) {
            val params =
                SendNotificationTestMessageInteractor.Params.Email(
                    smtpHost = emailSmtpHostState.text,
                    smtpPort = emailSmtpPortState.text.toIntOrNull() ?: 587,
                    username = emailUsernameState.text,
                    password = emailPasswordState.text,
                    from = emailFromState.text,
                    to = emailToState.text,
                    useTls = emailUseTls.value,
                )
            sendNotificationTestMessageInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    when {
                        result is SuspendableResult.Success -> {
                            setSuccessFeedback(FEEDBACK_TEST_NOTIFICATION_SUCCESS)
                        }

                        result is SuspendableResult.Failure -> {
                            setErrorFeedback(FEEDBACK_TEST_NOTIFICATION_FAILURE)
                        }
                    }
                }
            }
        }
    }

    fun saveAllNotifications() {
        val params =
            SaveAllNotificationSettingsInteractor.Params(
                discordEnabled = discordWebhookEnabledState.value,
                discordWebhookUrl = discordWebhookUrlState.text,
                telegramEnabled = telegramEnabled.value,
                telegramBotToken = telegramBotTokenState.text,
                telegramChatId = telegramChatIdState.text,
                desktopNotificationsEnabled = desktopNotificationsEnabled.value,
                emailEnabled = emailEnabled.value,
                emailSmtpHost = emailSmtpHostState.text,
                emailSmtpPort = emailSmtpPortState.text.toIntOrNull() ?: 587,
                emailUsername = emailUsernameState.text,
                emailPassword = emailPasswordState.text,
                emailFrom = emailFromState.text,
                emailTo = emailToState.text,
                emailUseTls = emailUseTls.value,
            )
        interactorRunner.launch(saveAllNotificationSettingsInteractor, params) {
            setSuccessFeedback(FEEDBACK_NOTIFICATION_SETTINGS_SAVED)
        }
    }

    fun checkForUpdates() {
        _isCheckingForUpdates.value = true
        scope.launch(dispatcherProvider.io) {
            checkForUpdatesInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    _isCheckingForUpdates.value = false
                    result.handleFailureOrElse(errorResolver) { checkResult ->
                        when (checkResult) {
                            is UpdateCheckResult.UpToDate -> {
                                setSuccessFeedback(FEEDBACK_UP_TO_DATE)
                            }

                            is UpdateCheckResult.UpdateAvailable -> {
                                _updateAvailableVersion.value = checkResult.version
                            }

                            is UpdateCheckResult.UpdateRequired -> {
                                _updateRequiredVersion.value = checkResult.version
                            }
                        }
                    }
                }
            }
        }
    }

    fun onConfirmUpdate(version: Version) {
        dismissUpdateDialogs()
        downloadJob =
            scope.launch(dispatcherProvider.io) {
                // Store builds: open store URL
                if (BuildConfig.IS_STORE_BUILD) {
                    version.storeUrl?.let { url ->
                        openUrlInteractor(OpenUrlInteractor.Params(url)) { }
                    }
                    return@launch
                }
                // Direct builds: download
                withContext(dispatcherProvider.main) {
                    _updateDownloadProgress.value = 0f
                }
                downloadLatestAppVersionInteractor(Interactor.None()).collectLatest { downloadResult ->
                    when (downloadResult) {
                        is SuspendableResult.Failure -> {
                            withContext(dispatcherProvider.main) {
                                dismissDownloadDialog()
                            }
                            errorResolver.setError(downloadResult.error)
                        }

                        is SuspendableResult.Success -> {
                            val status = downloadResult.value
                            withContext(dispatcherProvider.main) {
                                when (status) {
                                    is DownloadStatus.Downloading -> {
                                        _updateDownloadProgress.value = status.progress.toFloat() / PERCENT_MAX
                                        _updateDownloadStatus.value = DOWNLOAD_STATUS_DOWNLOADING
                                    }

                                    is DownloadStatus.Finished -> {
                                        _updateDownloadProgress.value = 1f
                                        _updateDownloadStatus.value = DOWNLOAD_STATUS_FINISHED
                                        pendingInstallerSha256 = status.sha256
                                        _openInstaller.value = status.file
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    fun dismissUpdateAvailableDialog() {
        _updateAvailableVersion.value = null
    }

    fun dismissUpdateDialogs() {
        _updateAvailableVersion.value = null
        _updateRequiredVersion.value = null
    }

    fun dismissDownloadDialog() {
        downloadJob?.cancel()
        downloadJob = null
        _updateDownloadProgress.value = null
        _updateDownloadStatus.value = ""
        _openInstaller.value = null
        pendingInstallerSha256 = null
    }

    fun openInstallerFile(file: File) {
        val params = InstallUpdateInteractor.Params(file, pendingInstallerSha256)
        interactorRunner.launch(installUpdateInteractor, params) { installResult ->
            when (installResult) {
                // Linux AppImage self-installed and relaunched: exit so it takes over.
                UpdateInstallResult.Relaunching -> exitProcess(0)

                // The OS opened the installer (macOS/Windows); settings stays running.
                UpdateInstallResult.Opened -> Unit

                // Couldn't launch the installer; point the user at the download so they
                // can install it manually (its folder was revealed where possible).
                UpdateInstallResult.Revealed,
                UpdateInstallResult.Failed,
                -> setErrorFeedback(FEEDBACK_INSTALLER_MANUAL.format(file.absolutePath))
            }
        }
    }

    companion object {
        private const val FEEDBACK_TEST_NOTIFICATION_SUCCESS = "Test notification sent successfully!"
        private const val FEEDBACK_TEST_NOTIFICATION_FAILURE =
            "Failed to send test notification. Please check your settings and try again."
        private const val FEEDBACK_NOTIFICATION_SETTINGS_SAVED = "Notification settings saved successfully!"
        private const val FEEDBACK_UP_TO_DATE = "You're up to date!"
        private const val FEEDBACK_INSTALLER_MANUAL =
            "Couldn't open the installer automatically. Your download is here: %s"
        private const val DOWNLOAD_STATUS_DOWNLOADING = "Downloading update..."
        private const val DOWNLOAD_STATUS_FINISHED = "Finished"
        private const val PERCENT_MAX = 100f
    }
}
