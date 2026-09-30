package com.cereal.client.application.interactor.bootstrap

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.app.VersionCheckService
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.MarketplaceUnreachableException
import com.cereal.client.application.proxy.ProxyHealthSweepScheduler
import com.cereal.client.application.script.PeriodicScriptSyncer
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import org.slf4j.LoggerFactory

@OptIn(FlowPreview::class)
class BootstrapInteractor(
    private val config: ApplicationConfig,
    private val systemRepository: SystemProvider,
    private val userAuthManager: UserAuthManager,
    private val periodicScriptSyncer: PeriodicScriptSyncer,
    private val versionCheckService: VersionCheckService,
    private val proxyHealthSweepScheduler: ProxyHealthSweepScheduler,
    private val notificationHistoryRepository: NotificationHistoryRepository,
) : FlowInteractor<BootstrapProgress, BootstrapInteractor.Params>() {
    private val logger = LoggerFactory.getLogger(BootstrapInteractor::class.java)

    enum class BootstrapSequenceIdentifier {
        CheckUpdate,
        BootingUp,
        CheckingAppFiles,
        CheckingForUpdates,
        Finishing,
    }

    private fun bootstrapSequence(params: Params) =
        mapOf(
            BootstrapSequenceIdentifier.CheckUpdate to
                flow {
                    emit(BootstrapProgress(BootstrapState.CheckingForUpdates, 0.0f))
                    checkForClientUpdates()?.let {
                        emit(
                            BootstrapProgress(
                                state = BootstrapState.Interrupted(it, BootstrapSequenceIdentifier.BootingUp),
                                0.0f,
                            ),
                        )
                    }
                },
            BootstrapSequenceIdentifier.BootingUp to
                flow {
                    emit(BootstrapProgress(BootstrapState.BootingUp, PROGRESS_BOOTING_UP))
                    startup(params.headless)
                },
            BootstrapSequenceIdentifier.CheckingAppFiles to
                flow {
                    emit(BootstrapProgress(BootstrapState.CheckingApplicationFiles, PROGRESS_CHECKING_APP_FILES))
                    createApplicationHomeFolder()
                },
            BootstrapSequenceIdentifier.CheckingForUpdates to
                flow {
                    emit(BootstrapProgress(BootstrapState.CheckingForUpdates, PROGRESS_RESTORING_USER))
                    restoreUser(params.headless)
                        ?.map {
                            when (it) {
                                UserAuthenticatingState.InitializingDiscord -> {
                                    BootstrapProgress(
                                        state = BootstrapState.InitializeDiscord,
                                        progress = 0.60f,
                                    )
                                }

                                is UserAuthenticatingState.SyncScripts -> {
                                    BootstrapProgress(
                                        state = BootstrapState.SynchronizeScripts,
                                        progress = 0.70f,
                                    )
                                }

                                UserAuthenticatingState.RestoreTasks -> {
                                    BootstrapProgress(
                                        state = BootstrapState.RestoringTasks,
                                        progress = 0.95f,
                                    )
                                }
                            }
                        }?.let {
                            emitAll(it)
                        }
                },
            BootstrapSequenceIdentifier.Finishing to
                flow {
                    emit(BootstrapProgress(BootstrapState.Finishing, PROGRESS_FINISHING))
                    finalize()
                    emit(BootstrapProgress(BootstrapState.Finished, 1.0f))
                },
        )

    override suspend fun run(params: Params): Flow<BootstrapProgress> =
        flow {
            bootstrapSequence(params)
                .asIterable()
                .dropWhile { params.startAt != null && params.startAt != it.key }
                .forEach { step ->
                    emitAll(step.value)
                }
        }.transformWhile {
            emit(it)
            it.state !is BootstrapState.Interrupted
        }

    /**
     * @param startAt if provided will start/continue the bootstrap process after the given [BootstrapSequenceIdentifier].
     * This is useful when somewhere in the process the users interaction is required to be able to continue the
     * bootstrap.
     * @param headless headless mode: no tray icon, and an unreachable marketplace while restoring the stored
     * session is retried with backoff (emitting [BootstrapState.MarketplaceUnreachable]) instead of falling
     * through to login as the desktop does.
     */
    data class Params(
        val startAt: BootstrapSequenceIdentifier? = null,
        val headless: Boolean = false,
    )

    private suspend fun startup(headless: Boolean) {
        logger.info("Booting up application.")
        if (!headless) systemRepository.createTrayIcon()
        periodicScriptSyncer.start()
        proxyHealthSweepScheduler.start()
    }

    private fun createApplicationHomeFolder() {
        logger.info("Checking application files.")

        val homeDirectory = config.homeDirectory
        if (!homeDirectory.exists()) {
            homeDirectory.mkdirs()
        }
    }

    private suspend fun FlowCollector<BootstrapProgress>.restoreUser(retryWhileUnreachable: Boolean): Flow<UserAuthenticatingState>? {
        var attempt = 0
        while (true) {
            try {
                return userAuthManager.initialize()
            } catch (e: MarketplaceUnreachableException) {
                if (!retryWhileUnreachable) {
                    // Desktop: an offline start lands on login, as it always has.
                    logger.warn("Marketplace unreachable while restoring the stored session.", e)
                    return null
                }
                logger.warn("Marketplace unreachable while restoring the stored session, retrying.", e)
                emit(BootstrapProgress(BootstrapState.MarketplaceUnreachable, PROGRESS_RESTORING_USER))
                delay(RESTORE_RETRY_BASE_MILLIS shl minOf(attempt++, RESTORE_RETRY_MAX_SHIFT))
            }
        }
    }

    private suspend fun checkForClientUpdates(): UserAction? =
        try {
            when (versionCheckService.checkForUpdates()) {
                is UpdateCheckResult.UpdateRequired -> UserAction.AppUpdateRequired
                is UpdateCheckResult.UpdateAvailable -> null
                is UpdateCheckResult.UpToDate -> null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logger.error("Failed to check for updates.", e)
            CrashReporter.report(e)
            null
        }

    private suspend fun finalize() {
        logger.info("Finished bootstrap.")
        pruneOldNotificationHistory()
        // Simulate a delay so user also sees this state
        delay(FINISH_STATE_VISIBILITY_DELAY_MILLIS)
    }

    private suspend fun pruneOldNotificationHistory() {
        try {
            val cutoff = System.currentTimeMillis() - NOTIFICATION_HISTORY_RETENTION_MILLIS
            notificationHistoryRepository.pruneOlderThan(cutoff)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logger.error("Failed to prune notification history during bootstrap.", e)
            CrashReporter.report(e)
        }
    }

    companion object {
        private const val FINISH_STATE_VISIBILITY_DELAY_MILLIS = 250L
        private const val PROGRESS_BOOTING_UP = 0.05f
        private const val PROGRESS_CHECKING_APP_FILES = 0.1f
        private const val PROGRESS_RESTORING_USER = 0.2f
        private const val PROGRESS_FINISHING = 0.99f

        /** Restore retries wait 1 s, 2 s, 4 s … capped at 64 s. */
        private const val RESTORE_RETRY_BASE_MILLIS = 1000L
        private const val RESTORE_RETRY_MAX_SHIFT = 6
        private const val NOTIFICATION_HISTORY_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
