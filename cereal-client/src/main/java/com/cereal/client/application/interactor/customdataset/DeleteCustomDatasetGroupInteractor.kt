package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.repository.CustomDatasetRepository

class DeleteCustomDatasetGroupInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : Interactor<Unit, DeleteCustomDatasetGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        customDatasetRepository.deleteDatasetGroup(params.customDatasetGroup)
    }

    data class Params(
        val customDatasetGroup: CustomDatasetGroup,
    )
}
