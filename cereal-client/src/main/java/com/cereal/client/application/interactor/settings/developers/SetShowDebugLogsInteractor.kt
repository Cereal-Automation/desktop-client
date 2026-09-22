package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ApplicationPreferenceRepository

class SetShowDebugLogsInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : Interactor<Unit, SetShowDebugLogsInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setShowDebugLogs(params.enabled)
    }

    data class Params(
        val enabled: Boolean,
    )
}
