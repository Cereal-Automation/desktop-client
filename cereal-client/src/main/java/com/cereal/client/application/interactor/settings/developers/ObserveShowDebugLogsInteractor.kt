package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class ObserveShowDebugLogsInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : FlowInteractor<Boolean, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Boolean> = applicationPreferenceRepository.isShowDebugLogs()
}
