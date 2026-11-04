package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.domain.repository.DiagnosticsSharingRepository

/**
 * Takes effect straight away, also for runs already going: the reporters check the choice again before each report.
 * Turning it off also takes back failure reports waiting to retry, so turning it on again doesn't send them.
 */
class SetShareDiagnosticsEnabledInteractor(
    private val diagnosticsSharingRepository: DiagnosticsSharingRepository,
    private val failureReporter: FailureReporter,
) : Interactor<Unit, SetShareDiagnosticsEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        diagnosticsSharingRepository.setEnabled(params.enabled)
        if (!params.enabled) failureReporter.resumePendingUploads()
    }

    data class Params(
        val enabled: Boolean,
    )
}
