package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.repository.CustomDatasetRepository

class UpdateCustomDatasetGroupInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : Interactor<Unit, UpdateCustomDatasetGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        val newGroup = params.group.copy(name = params.name)
        customDatasetRepository.updateDatasetGroup(newGroup)
    }

    data class Params(
        val group: CustomDatasetGroup,
        val name: String,
    )
}
