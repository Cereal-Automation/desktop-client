package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.datasets.toInvalidFileException
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.InvalidDatasetFileException
import com.cereal.client.domain.model.datasets.toDatasetItems
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import kotlinx.coroutines.flow.Flow
import java.io.File

class CustomDatasetRepositoryImpl(
    private val datasetDataSource: DatasetDataSource,
    private val userSession: UserSession,
    private val csvReader: CsvReader,
) : CustomDatasetRepository {
    override suspend fun createDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        datasetDataSource.createDatasetGroup(
            userSession.requireUser(),
            customDatasetGroup,
        )

    override suspend fun updateDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        datasetDataSource.updateDatasetGroup(
            userSession.requireUser(),
            customDatasetGroup,
        )

    override suspend fun deleteDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        datasetDataSource.deleteDatasetGroup(
            userSession.requireUser(),
            customDatasetGroup.id,
        )

    override suspend fun getDatasetGroups(): Flow<List<CustomDatasetGroup>> = datasetDataSource.getDatasetGroups(userSession.requireUser())

    override suspend fun getDatasets(customDatasetGroup: CustomDatasetGroup): Flow<List<CustomDatasetItem>> = datasetDataSource.getDatasetsFlow(userSession.requireUser(), customDatasetGroup.id)

    override suspend fun createOrUpdateDataset(
        customDatasetItem: CustomDatasetItem,
        customDatasetGroup: CustomDatasetGroup,
    ) = datasetDataSource.createOrUpdateDataset(
        userSession.requireUser(),
        customDatasetItem,
        customDatasetGroup.id,
    )

    override suspend fun deleteDataset(customDatasetItem: CustomDatasetItem) = datasetDataSource.deleteDataset(userSession.requireUser(), customDatasetItem.id)

    override suspend fun getDatasetsFromGroup(datasetGroupId: String): List<CustomDatasetItem> =
        datasetDataSource.getDatasetsFromGroup(
            userSession.requireUser(),
            datasetGroupId,
        )

    override suspend fun getDatasetsInGroupCount(datasetGroupId: String): Long =
        datasetDataSource.getDatasetsInGroupCount(
            userSession.requireUser(),
            datasetGroupId,
        )

    override suspend fun readFromFile(
        file: File,
        definitions: List<ScriptConfigurationItemDefinition>,
    ): List<CustomDatasetItem> =
        // TODO: Optimize by returning a Sequence instead of a list for better memory management.
        try {
            definitions.toDatasetItems(csvReader.readRawRows(file))
        } catch (e: InvalidDatasetFileException) {
            throw e.toInvalidFileException()
        }
}
