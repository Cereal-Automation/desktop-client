package com.cereal.client.application.interactor.settings.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.ApplicationPreferenceRepository

class SetProxyHealthCheckIntervalInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : Interactor<Unit, SetProxyHealthCheckIntervalInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setProxyHealthCheckInterval(params.interval)
    }

    data class Params(
        val interval: ProxyHealthCheckInterval,
    )
}
