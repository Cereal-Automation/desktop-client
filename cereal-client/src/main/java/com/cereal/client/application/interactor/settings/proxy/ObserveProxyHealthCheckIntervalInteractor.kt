package com.cereal.client.application.interactor.settings.proxy

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class ObserveProxyHealthCheckIntervalInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : FlowInteractor<ProxyHealthCheckInterval, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<ProxyHealthCheckInterval> = applicationPreferenceRepository.getProxyHealthCheckInterval()
}
