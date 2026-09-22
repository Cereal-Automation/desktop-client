package com.cereal.client.application.proxy

import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Re-checks proxies whose last health probe is older than the user-selected
 * `staleThreshold` (e.g. 6h, 24h). Active only while a user is authenticated
 * and the [ProxyHealthCheckInterval] preference is not [ProxyHealthCheckInterval.OFF].
 *
 * Single instance — duplicate starts would cause overlapping sweeps.
 */
class ProxyHealthSweepScheduler(
    private val proxyRepository: ProxyRepository,
    private val proxyHealthChecker: ProxyHealthChecker,
    private val userAuthManager: UserAuthManager,
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(ProxyHealthSweepScheduler::class.java)

    private var sweepJob: Job? = null
    private var observeJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (observeJob?.isActive == true) return

        observeJob =
            scope.launch {
                userAuthManager
                    .getAuthenticatedUserFlow()
                    .flatMapLatest { user ->
                        // The preference flow requires an authenticated user; only subscribe
                        // to it once a user is present, otherwise it throws on access.
                        val intervalFlow =
                            if (user != null) {
                                applicationPreferenceRepository.getProxyHealthCheckInterval()
                            } else {
                                flowOf<ProxyHealthCheckInterval?>(null)
                            }
                        intervalFlow.map { interval -> interval?.staleThreshold }
                    }.collectLatest { threshold ->
                        sweepJob?.cancel()
                        sweepJob = null

                        if (threshold != null) {
                            sweepJob = scope.launch { sweepLoop(threshold) }
                        }
                    }
            }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
        sweepJob?.cancel()
        sweepJob = null
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun sweepLoop(staleThreshold: Duration) {
        while (true) {
            try {
                runSweep(staleThreshold)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Proxy health sweep failed; will retry next cycle.", e)
                CrashReporter.report(e)
            }
            delay(POLL_INTERVAL.inWholeMilliseconds)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runSweep(staleThreshold: Duration) {
        val cutoff = Clock.System.now() - staleThreshold
        val stale = proxyRepository.getStaleProxies(cutoff)
        if (stale.isEmpty()) return

        val semaphore = Semaphore(SWEEP_BATCH)
        coroutineScope {
            stale.forEach { proxy ->
                launch {
                    semaphore.withPermit {
                        val health = proxyHealthChecker.check(proxy)
                        proxyRepository.updateProxyHealth(proxy.id, health)
                    }
                }
            }
        }
    }

    companion object {
        // How often we poll for stale proxies. Cheap (single SQL query) and independent of
        // probe cost — the actual probe rate is governed by `staleThreshold`.
        @OptIn(ExperimentalTime::class)
        private val POLL_INTERVAL = 30.minutes

        private const val SWEEP_BATCH = 8
    }
}
