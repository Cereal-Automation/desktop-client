package com.cereal.client.application.interactor.bootstrap

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.app.VersionCheckService
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.proxy.ProxyHealthSweepScheduler
import com.cereal.client.application.script.PeriodicScriptSyncer
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.domain.repository.NotificationHistoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BootstrapInteractorTest {
    private lateinit var config: ApplicationConfig
    private lateinit var systemRepository: SystemProvider
    private lateinit var userAuthManager: UserAuthManager
    private lateinit var periodicScriptSyncer: PeriodicScriptSyncer
    private lateinit var versionCheckService: VersionCheckService
    private lateinit var proxyHealthSweepScheduler: ProxyHealthSweepScheduler
    private lateinit var notificationHistoryRepository: NotificationHistoryRepository
    private lateinit var interactor: BootstrapInteractor

    @BeforeEach
    fun setUp() {
        config = mockk(relaxed = true)
        systemRepository = mockk(relaxed = true)
        userAuthManager = mockk(relaxed = true)
        periodicScriptSyncer = mockk(relaxed = true)
        versionCheckService = mockk(relaxed = true)
        proxyHealthSweepScheduler = mockk(relaxed = true)
        notificationHistoryRepository = mockk(relaxed = true)
        interactor =
            BootstrapInteractor(
                config = config,
                systemRepository = systemRepository,
                userAuthManager = userAuthManager,
                periodicScriptSyncer = periodicScriptSyncer,
                versionCheckService = versionCheckService,
                proxyHealthSweepScheduler = proxyHealthSweepScheduler,
                notificationHistoryRepository = notificationHistoryRepository,
            )
    }

    @Test
    fun `run interrupts the sequence when a client update is required`() =
        runTest {
            val version =
                Version(
                    SemVer(2, 0, 0),
                    SemVer(2, 0, 0),
                    downloadUrl = "https://downloads.example.com/cereal-client-latest.exe",
                )
            coEvery { versionCheckService.checkForUpdates() } returns UpdateCheckResult.UpdateRequired(version)

            val results = interactor.run(BootstrapInteractor.Params()).toList()

            // First the update-check state is emitted, then the interrupt; nothing after it.
            assertEquals(2, results.size)
            assertEquals(BootstrapState.CheckingForUpdates, results.first().state)
            val interrupted = assertInstanceOf(BootstrapState.Interrupted::class.java, results.last().state)
            assertEquals(UserAction.AppUpdateRequired, interrupted.action)
        }

    @Test
    fun `run resumes from a given step and only emits the remaining states`() =
        runTest {
            val results =
                interactor
                    .run(
                        BootstrapInteractor.Params(
                            startAt = BootstrapInteractor.BootstrapSequenceIdentifier.Finishing,
                        ),
                    ).toList()

            assertEquals(
                listOf(BootstrapState.Finishing, BootstrapState.Finished),
                results.map { it.state },
            )
        }
}
