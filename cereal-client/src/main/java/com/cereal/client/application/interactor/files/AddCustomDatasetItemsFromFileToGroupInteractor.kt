package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.repository.CustomDatasetRepository
import java.io.File

class AddCustomDatasetItemsFromFileToGroupInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : Interactor<AddCustomDatasetItemsFromFileToGroupInteractor.Result, AddCustomDatasetItemsFromFileToGroupInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val customDatasets =
            customDatasetRepository.readFromFile(
                params.file,
                params.group.itemDefinitions,
            )

        customDatasets.forEach {
            customDatasetRepository.createOrUpdateDataset(it, params.group)
        }

        return Result(params.group.copy(numberOfItems = customDatasets.size))
    }

    data class Params(
        val file: File,
        val group: CustomDatasetGroup,
    )

    data class Result(
        val group: CustomDatasetGroup,
    )
}
