package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.repository.CustomDatasetRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetCustomDatasetGroupsInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : FlowInteractor<List<CustomDatasetGroup>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<CustomDatasetGroup>> = customDatasetRepository.getDatasetGroups()
}
