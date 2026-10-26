package com.cereal.client.application.interactor.task

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class ObserveTaskSetupSortInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : FlowInteractor<TaskSetupSort, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<TaskSetupSort> = applicationPreferenceRepository.getTaskSetupSort()
}
