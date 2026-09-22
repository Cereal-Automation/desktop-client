package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.CrashReportingProvider

class SetCrashReportingEnabledInteractor(
    private val crashReportingProvider: CrashReportingProvider,
) : Interactor<Unit, SetCrashReportingEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        crashReportingProvider.setEnabled(params.enabled)
    }

    data class Params(
        val enabled: Boolean,
    )
}
