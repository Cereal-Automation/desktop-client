package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.CrashReportingProvider

class GetCrashReportingEnabledInteractor(
    private val crashReportingProvider: CrashReportingProvider,
) : Interactor<Boolean, Interactor.None>() {
    override suspend fun run(params: None): Boolean = crashReportingProvider.isEnabled()
}
