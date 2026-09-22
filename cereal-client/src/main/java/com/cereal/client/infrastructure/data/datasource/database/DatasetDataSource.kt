package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DatasetDataSource {
    suspend fun getDatasetGroups(user: User): Flow<List<CustomDatasetGroup>>

    suspend fun createDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    )

    suspend fun updateDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    )

    suspend fun deleteDatasetGroup(
        user: User,
        customDatasetGroupId: String,
    )

    suspend fun getDatasetGroup(
        user: User,
        id: String,
    ): CustomDatasetGroup?

    suspend fun getDatasetsFromGroup(
        user: User,
        datasetGroupId: String,
    ): List<CustomDatasetItem>

    suspend fun getDatasetsInGroupCount(
        user: User,
        datasetGroupId: String,
    ): Long

    fun getDatasetsFlow(
        user: User,
        customDatasetGroupId: String,
    ): Flow<List<CustomDatasetItem>>

    suspend fun getDataset(
        user: User,
        id: String,
    ): CustomDatasetItem?

    suspend fun createOrUpdateDataset(
        user: User,
        customDatasetItem: CustomDatasetItem,
        groupId: String,
    )

    suspend fun deleteDataset(
        user: User,
        customDatasetItemId: UUID,
    )
}
