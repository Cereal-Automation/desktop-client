package com.cereal.client.fixtures

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory fake of [DatasetDataSource].
 *
 * Groups (with their definitions and createdAt preserved) and dataset items are stored in plain
 * maps keyed by user id (and, for items, by group id). Flow getters are driven by a monotonic
 * [revision] counter rather than by content equality: [CustomDatasetGroup.equals] is id-only, so a
 * StateFlow<Map<…, group>> would conflate a rename (same id, new name) as "no change" and never
 * emit. Bumping an Int revision on every write guarantees observers re-read the current state.
 *
 * Returned [CustomDatasetGroup]s are recomputed from the current items so
 * [CustomDatasetGroup.numberOfItems] / [CustomDatasetGroup.items] stay consistent, while preserving
 * the stored [CustomDatasetGroup.itemDefinitions] and [CustomDatasetGroup.createdAt].
 */
class InMemoryDatasetDataSource : DatasetDataSource {
    // user id -> (group id -> stored group template)
    private var groupsByUser: Map<String, Map<String, CustomDatasetGroup>> = emptyMap()

    // user id -> (group id -> (item id -> item))
    private var itemsByUser: Map<String, Map<String, Map<UUID, CustomDatasetItem>>> = emptyMap()

    // Emission token: incremented on every mutation so Flow getters re-read current state.
    private val revision = MutableStateFlow(0L)

    private fun bump() {
        revision.value += 1
    }

    private fun itemsIn(
        userId: String,
        groupId: String,
    ): Map<UUID, CustomDatasetItem> = itemsByUser[userId]?.get(groupId).orEmpty()

    private fun buildGroup(
        userId: String,
        template: CustomDatasetGroup,
    ): CustomDatasetGroup {
        val groupItems = itemsIn(userId, template.id).values.toList()
        return template.copy(
            numberOfItems = groupItems.size,
            items = groupItems.asSequence(),
        )
    }

    override suspend fun getDatasetGroups(user: User): Flow<List<CustomDatasetGroup>> =
        revision.map {
            groupsByUser[user.id].orEmpty().values.map { buildGroup(user.id, it) }
        }

    override suspend fun createDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    ) {
        val userGroups = groupsByUser[user.id].orEmpty()
        groupsByUser = groupsByUser + (user.id to (userGroups + (customDatasetGroup.id to customDatasetGroup)))
        bump()
        customDatasetGroup.items.forEach { item ->
            createOrUpdateDataset(user, item, customDatasetGroup.id)
        }
    }

    override suspend fun updateDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    ) {
        val userGroups = groupsByUser[user.id].orEmpty()
        if (customDatasetGroup.id in userGroups) {
            groupsByUser = groupsByUser + (user.id to (userGroups + (customDatasetGroup.id to customDatasetGroup)))
            bump()
        }
    }

    override suspend fun deleteDatasetGroup(
        user: User,
        customDatasetGroupId: String,
    ) {
        val userGroups = groupsByUser[user.id].orEmpty()
        groupsByUser = groupsByUser + (user.id to (userGroups - customDatasetGroupId))
        val userItems = itemsByUser[user.id].orEmpty()
        itemsByUser = itemsByUser + (user.id to (userItems - customDatasetGroupId))
        bump()
    }

    override suspend fun getDatasetGroup(
        user: User,
        id: String,
    ): CustomDatasetGroup? {
        val template = groupsByUser[user.id]?.get(id) ?: return null
        return buildGroup(user.id, template)
    }

    override suspend fun getDatasetsFromGroup(
        user: User,
        datasetGroupId: String,
    ): List<CustomDatasetItem> = itemsIn(user.id, datasetGroupId).values.toList()

    override suspend fun getDatasetsInGroupCount(
        user: User,
        datasetGroupId: String,
    ): Long = itemsIn(user.id, datasetGroupId).size.toLong()

    override fun getDatasetsFlow(
        user: User,
        customDatasetGroupId: String,
    ): Flow<List<CustomDatasetItem>> =
        revision.map {
            itemsIn(user.id, customDatasetGroupId).values.toList()
        }

    override suspend fun getDataset(
        user: User,
        id: String,
    ): CustomDatasetItem? {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return itemsByUser[user.id]
            ?.values
            ?.firstNotNullOfOrNull { it[uuid] }
    }

    override suspend fun createOrUpdateDataset(
        user: User,
        customDatasetItem: CustomDatasetItem,
        groupId: String,
    ) {
        val userItems = itemsByUser[user.id].orEmpty()
        val groupItems = userItems[groupId].orEmpty()
        val updatedGroup = groupItems + (customDatasetItem.id to customDatasetItem)
        itemsByUser = itemsByUser + (user.id to (userItems + (groupId to updatedGroup)))
        bump()
    }

    override suspend fun deleteDataset(
        user: User,
        customDatasetItemId: UUID,
    ) {
        val userItems = itemsByUser[user.id].orEmpty()
        val updated =
            userItems.mapValues { (_, groupItems) ->
                groupItems - customDatasetItemId
            }
        itemsByUser = itemsByUser + (user.id to updated)
        bump()
    }
}
