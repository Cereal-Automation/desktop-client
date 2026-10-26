package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.domain.repository.ApplicationPreferenceRepository

class SetTaskSetupSortInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : Interactor<Unit, SetTaskSetupSortInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setTaskSetupSort(params.sort)
    }

    data class Params(
        val sort: TaskSetupSort,
    )
}
