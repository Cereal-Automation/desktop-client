package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.repository.CustomDatasetRepository

class DeleteDatasetItemInteractor(
    private val datasetRepository: CustomDatasetRepository,
) : Interactor<Unit, DeleteDatasetItemInteractor.Params>() {
    override suspend fun run(params: Params) {
        datasetRepository.deleteDataset(params.customDatasetItem)
    }

    data class Params(
        val customDatasetItem: CustomDatasetItem,
    )
}
