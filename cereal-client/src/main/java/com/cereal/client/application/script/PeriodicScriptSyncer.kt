package com.cereal.client.application.script

import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.exception.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Periodically syncs installed scripts with marketplace subscriptions.
 *
 * This class MUST be a single instance to avoid duplicate sync loops.
 */
class PeriodicScriptSyncer(
    private val scriptSyncManager: ScriptSyncManager,
    private val userAuthManager: UserAuthManager,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(PeriodicScriptSyncer::class.java)
    private var syncJob: Job? = null
    private var observeJob: Job? = null
    private var failCount = 0

    fun start() {
        // Guard against multiple start() calls leaking observer coroutines.
        if (observeJob?.isActive == true) return

        observeJob =
            scope.launch {
                userAuthManager.getAuthenticatedUserFlow().collectLatest {
                    if (it != null && syncJob?.isActive != true) {
                        syncJob =
                            scope.launch {
                                while (true) {
                                    delay(DELAY)
                                    try {
                                        scriptSyncManager.sync(updateScripts = false)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failCount++
                                        logger.warn("Periodic script sync failed ($failCount/$MAX_FAIL_COUNT).", e)
                                        CrashReporter.report(e, mapOf("fail_count" to failCount))
                                        if (failCount >= MAX_FAIL_COUNT) {
                                            userAuthManager.deauthenticate()
                                        }
                                    }
                                }
                            }
                    } else if (it == null && syncJob != null) {
                        syncJob?.cancel()
                        syncJob = null
                        failCount = 0
                    }
                }
            }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
        syncJob?.cancel()
        syncJob = null
        failCount = 0
    }

    companion object {
        private const val MAX_FAIL_COUNT: Int = 3

        // 4 hours
        private const val DELAY: Long = 1000L * 60 * 60 * 4
    }
}
