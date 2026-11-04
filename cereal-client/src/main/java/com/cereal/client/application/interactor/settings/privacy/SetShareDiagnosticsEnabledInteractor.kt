package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.DiagnosticsSharingRepository

/** Takes effect straight away, also for runs already going: the reporter checks the choice again at their finish. */
class SetShareDiagnosticsEnabledInteractor(
    private val diagnosticsSharingRepository: DiagnosticsSharingRepository,
) : Interactor<Unit, SetShareDiagnosticsEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        diagnosticsSharingRepository.setEnabled(params.enabled)
    }

    data class Params(
        val enabled: Boolean,
    )
}
