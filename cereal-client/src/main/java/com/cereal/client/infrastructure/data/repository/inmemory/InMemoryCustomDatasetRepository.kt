package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.repository.CustomDatasetRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.time.ExperimentalTime

/**
 * In-memory dataset repository. Holds no built-in sample data: state is whatever the caller injects
 * via [initialGroups] (empty by default). The `mock` flavor injects sample groups through the DI
 * module; tests pass their own or none. Backed by [MutableStateFlow] so edits and deletes update
 * the table.
 *
 * [readFromFile] is not modelled (there is no real file parsing here); it returns [fileItems] by
 * default, or throws [readError] when one is supplied, so file-import interactor tests can drive
 * both the success and failure paths without a separate fake.
 */
@OptIn(ExperimentalTime::class)
class InMemoryCustomDatasetRepository(
    initialGroups: List<CustomDatasetGroup> = emptyList(),
    private val fileItems: List<CustomDatasetItem> = emptyList(),
    private val readError: Throwable? = null,
) : CustomDatasetRepository {
    private val mutex = Mutex()
    private val items: MutableMap<String, MutableStateFlow<List<CustomDatasetItem>>> =
        initialGroups.associateTo(mutableMapOf()) { it.id to MutableStateFlow(it.items.toList()) }

    @Volatile private var currentGroups: List<CustomDatasetGroup> = initialGroups

    // SharedFlow (not StateFlow) because CustomDatasetGroup.equals is ID-only:
    // a renamed group produces a list structurally equal to the previous one,
    // which StateFlow would dedupe and the UI would never see the rename.
    private val groupsFlow: MutableSharedFlow<List<CustomDatasetGroup>> =
        MutableSharedFlow<List<CustomDatasetGroup>>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ).apply { tryEmit(currentGroups) }

    override suspend fun createDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        mutex.withLock {
            items[customDatasetGroup.id] = MutableStateFlow(emptyList())
            emitGroupsLocked(currentGroups + customDatasetGroup.copy(numberOfItems = 0, items = emptySequence()))
        }

    override suspend fun updateDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        mutex.withLock {
            emitGroupsLocked(
                currentGroups.map { group ->
                    if (group.id == customDatasetGroup.id) {
                        customDatasetGroup.copy(createdAt = group.createdAt)
                    } else {
                        group
                    }
                },
            )
        }

    override suspend fun deleteDatasetGroup(customDatasetGroup: CustomDatasetGroup) =
        mutex.withLock {
            items.remove(customDatasetGroup.id)
            emitGroupsLocked(currentGroups.filter { it.id != customDatasetGroup.id })
        }

    override suspend fun getDatasetGroups(): Flow<List<CustomDatasetGroup>> = groupsFlow

    override suspend fun getDatasets(customDatasetGroup: CustomDatasetGroup): Flow<List<CustomDatasetItem>> = mutex.withLock { items.getOrPut(customDatasetGroup.id) { MutableStateFlow(emptyList()) } }

    override suspend fun createOrUpdateDataset(
        customDatasetItem: CustomDatasetItem,
        customDatasetGroup: CustomDatasetGroup,
    ) = mutex.withLock {
        val flow = items.getOrPut(customDatasetGroup.id) { MutableStateFlow(emptyList()) }
        val current = flow.value
        val updated =
            if (current.any { it.id == customDatasetItem.id }) {
                current.map { if (it.id == customDatasetItem.id) customDatasetItem else it }
            } else {
                current + customDatasetItem
            }
        flow.value = updated
        bumpGroupCountLocked(customDatasetGroup.id, updated.size)
    }

    override suspend fun deleteDataset(customDatasetItem: CustomDatasetItem) =
        mutex.withLock {
            items.forEach { (groupId, flow) ->
                if (flow.value.any { it.id == customDatasetItem.id }) {
                    flow.value = flow.value.filter { it.id != customDatasetItem.id }
                    bumpGroupCountLocked(groupId, flow.value.size)
                }
            }
        }

    override suspend fun getDatasetsFromGroup(datasetGroupId: String): List<CustomDatasetItem> = mutex.withLock { items[datasetGroupId]?.value ?: emptyList() }

    override suspend fun getDatasetsInGroupCount(datasetGroupId: String): Long = mutex.withLock { items[datasetGroupId]?.value?.size?.toLong() ?: 0L }

    override suspend fun readFromFile(
        file: File,
        definitions: List<ScriptConfigurationItemDefinition>,
    ): List<CustomDatasetItem> {
        readError?.let { throw it }
        return fileItems
    }

    private fun bumpGroupCountLocked(
        groupId: String,
        newCount: Int,
    ) {
        emitGroupsLocked(
            currentGroups.map { group ->
                if (group.id == groupId) group.copy(numberOfItems = newCount) else group
            },
        )
    }

    private fun emitGroupsLocked(newGroups: List<CustomDatasetGroup>) {
        currentGroups = newGroups
        groupsFlow.tryEmit(newGroups)
    }
}
