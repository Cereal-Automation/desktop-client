package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.DiagnosticsSharingRepository

class GetShareDiagnosticsEnabledInteractor(
    private val diagnosticsSharingRepository: DiagnosticsSharingRepository,
) : Interactor<Boolean, Interactor.None>() {
    override suspend fun run(params: None): Boolean = diagnosticsSharingRepository.isEnabled()
}
