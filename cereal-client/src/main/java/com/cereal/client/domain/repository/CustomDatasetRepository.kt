package com.cereal.client.domain.repository

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import kotlinx.coroutines.flow.Flow
import java.io.File

interface CustomDatasetRepository {
    suspend fun createDatasetGroup(customDatasetGroup: CustomDatasetGroup)

    suspend fun updateDatasetGroup(customDatasetGroup: CustomDatasetGroup)

    suspend fun deleteDatasetGroup(customDatasetGroup: CustomDatasetGroup)

    suspend fun getDatasetGroups(): Flow<List<CustomDatasetGroup>>

    suspend fun getDatasets(customDatasetGroup: CustomDatasetGroup): Flow<List<CustomDatasetItem>>

    suspend fun createOrUpdateDataset(
        customDatasetItem: CustomDatasetItem,
        customDatasetGroup: CustomDatasetGroup,
    )

    suspend fun deleteDataset(customDatasetItem: CustomDatasetItem)

    suspend fun getDatasetsFromGroup(datasetGroupId: String): List<CustomDatasetItem>

    suspend fun getDatasetsInGroupCount(datasetGroupId: String): Long

    suspend fun readFromFile(
        file: File,
        definitions: List<ScriptConfigurationItemDefinition>,
    ): List<CustomDatasetItem>
}
