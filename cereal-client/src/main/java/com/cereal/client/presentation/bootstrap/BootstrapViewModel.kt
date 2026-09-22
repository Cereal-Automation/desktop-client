package com.cereal.client.presentation.bootstrap

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.app.DownloadLatestAppVersionInteractor
import com.cereal.client.application.interactor.app.InstallUpdateInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapState
import com.cereal.client.application.interactor.bootstrap.UserAction
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal_automation.cereal_client.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BootstrapViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val bootstrapInteractor: BootstrapInteractor,
    private val downloadLatestAppVersionInteractor: DownloadLatestAppVersionInteractor,
    private val errorResolver: ErrorResolver,
    private val installUpdateInteractor: InstallUpdateInteractor,
    private val openUrlInteractor: OpenUrlInteractor,
    private val appUpdateProvider: AppUpdateProvider,
) {
    val windowClosed = mutableStateOf(false)
    val progress = mutableStateOf(0f)
    val progressStatus = mutableStateOf("")
    val openInstaller = mutableStateOf<File?>(null)

    /**
     * Set when the installer could not be launched automatically. The screen shows a "your download
     * is here" message pointing at this file so the user can install it manually.
     */
    val installerLocation = mutableStateOf<File?>(null)

    /**
     * Expected SHA-256 of the downloaded installer (from the release metadata), kept alongside
     * [openInstaller] so [installUpdate] can have it re-verified right before launch.
     */
    private var pendingInstallerSha256: String? = null
    val showUserAction = mutableStateOf<UserAction?>(null)
    val showLoadingIndicator = mutableStateOf(false)
    val shouldExitApp = mutableStateOf(false)
    val errorAction = errorResolver.errorAction
    var bootstrapJob: Job? = null
    private var loadingIndicatorJob: Job? = null
    private var currentInterruptedState: BootstrapState.Interrupted? = null
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    init {
        bootstrap()
    }

    private fun onUpdateApp() {
        showUserAction.value = null

        scope.launch(dispatcherProvider.io) {
            // For store builds, open the store URL instead of downloading
            if (BuildConfig.IS_STORE_BUILD) {
                val latestVersion = appUpdateProvider.getLatestAvailableAppVersion()
                latestVersion.storeUrl?.let { storeUrl ->
                    openUrlInteractor(OpenUrlInteractor.Params(storeUrl)) { _ -> }
                    shouldExitApp.value = true
                }
                return@launch
            }

            downloadLatestAppVersionInteractor(Interactor.None()).collectLatest {
                withContext(dispatcherProvider.main) {
                    it.handleFailureOrElse(errorResolver) {
                        when (it) {
                            is DownloadStatus.Downloading -> {
                                progress.value = it.progress.toFloat() / PERCENT_MAX
                                progressStatus.value = "Downloading update..."
                                resetLoadingIndicatorTimer()
                            }

                            is DownloadStatus.Finished -> {
                                progress.value = 1f
                                progressStatus.value = "Finished"
                                pendingInstallerSha256 = it.sha256
                                openInstaller.value = it.file
                                cancelLoadingIndicatorTimer()
                            }
                        }
                    }
                }
            }
        }
    }

    fun onConfirmUserAction() {
        showUserAction.value = null
        currentInterruptedState = null
        onUpdateApp()
    }

    fun onDismissUserAction() {
        val state = currentInterruptedState ?: return
        showUserAction.value = null
        currentInterruptedState = null
        bootstrap(state)
    }

    private fun bootstrap(continueAfterInterruption: BootstrapState.Interrupted? = null) {
        bootstrapJob?.cancel()
        resetLoadingIndicatorTimer()
        bootstrapJob =
            scope.launch(dispatcherProvider.io) {
                bootstrapInteractor(BootstrapInteractor.Params(continueAfterInterruption?.continueAt)).collectLatest {
                    withContext(dispatcherProvider.main) {
                        it.handleFailureOrElse(errorResolver) {
                            progress.value = it.progress
                            progressStatus.value = it.state.toText()
                            resetLoadingIndicatorTimer()

                            if (it.state == BootstrapState.Finished) {
                                windowClosed.value = true
                                cancelLoadingIndicatorTimer()
                            } else if (it.state is BootstrapState.Interrupted) {
                                handleInterruptedState(it.state)
                            }
                        }
                    }
                }
            }
    }

    private fun handleInterruptedState(state: BootstrapState.Interrupted) {
        currentInterruptedState = state
        showUserAction.value = state.action
    }

    private fun resetLoadingIndicatorTimer() {
        loadingIndicatorJob?.cancel()
        showLoadingIndicator.value = false

        loadingIndicatorJob =
            scope.launch(dispatcherProvider.main) {
                delay(LOADING_INDICATOR_DELAY)
                showLoadingIndicator.value = true
            }
    }

    private fun cancelLoadingIndicatorTimer() {
        loadingIndicatorJob?.cancel()
        showLoadingIndicator.value = false
    }

    fun installUpdate(file: File) {
        val params = InstallUpdateInteractor.Params(file, pendingInstallerSha256)
        interactorRunner.launch(installUpdateInteractor, params) { installResult ->
            when (installResult) {
                // Linux AppImage self-installed and relaunched, or the OS installer
                // launched: quit (via shouldExitApp) so the new build takes over.
                UpdateInstallResult.Relaunching,
                UpdateInstallResult.Opened,
                -> {
                    shouldExitApp.value = true
                }

                // Could not launch it; keep the app open and tell the user where the
                // download is so they can install it manually.
                UpdateInstallResult.Revealed,
                UpdateInstallResult.Failed,
                -> {
                    openInstaller.value = null
                    installerLocation.value = file
                }
            }
        }
    }

    private fun BootstrapState.toText(): String =
        when (this) {
            BootstrapState.BootingUp -> "Booting"
            BootstrapState.CheckingApplicationFiles -> "Checking application files"
            BootstrapState.LoadingScriptConfigurationFiles -> "Loading script configuration files"
            BootstrapState.InitializeDiscord -> "Initialize Discord RPC-connection"
            BootstrapState.ValidatingDirectories -> "Validating application files"
            BootstrapState.Finishing -> "Starting Cereal"
            BootstrapState.Finished -> "Bootstrap finished"
            BootstrapState.RestoringTasks -> "Restoring tasks"
            BootstrapState.RestoreUser -> "Checking authentication"
            BootstrapState.SynchronizeScripts -> "Updating scripts"
            BootstrapState.CheckingForUpdates -> "Checking for updates"
            is BootstrapState.Interrupted -> "Waiting user response..."
        }

    companion object {
        const val LOADING_INDICATOR_DELAY: Long = 2500
        private const val PERCENT_MAX = 100f
    }
}
