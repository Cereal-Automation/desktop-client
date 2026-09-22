package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.repository.CustomDatasetRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetCustomDatasetsInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : FlowInteractor<List<CustomDatasetItem>, GetCustomDatasetsInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<CustomDatasetItem>> = customDatasetRepository.getDatasets(params.customDatasetGroup)

    data class Params(
        val customDatasetGroup: CustomDatasetGroup,
    )
}
