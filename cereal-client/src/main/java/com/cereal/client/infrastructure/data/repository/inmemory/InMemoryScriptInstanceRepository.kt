package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.ScriptInstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ScriptInstanceRepository] for screen tests. Starts empty.
 *
 * Instances added through [addScriptPackageInstance] belong to that group (and can be moved);
 * instances [seed]ed without a group show up in every group.
 */
class InMemoryScriptInstanceRepository : ScriptInstanceRepository {
    private data class Entry(
        val packageInstance: ScriptPackageInstance,
        val groupId: String? = null,
        val mainScriptInstance: MainScriptInstance? = null,
        val childScriptInstances: List<ChildScriptInstance> = emptyList(),
    )

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    fun seed(packageInstances: List<ScriptPackageInstance>) {
        entries.value = packageInstances.map { Entry(it) }
    }

    override suspend fun addChildScriptInstance(
        parentInstance: ScriptInstance,
        name: String,
        scriptInstance: ChildScriptInstance,
    ) {
        entries.value =
            entries.value.map {
                if (it.packageInstance.id == scriptInstance.packageInstance.id) it.copy(childScriptInstances = it.childScriptInstances + scriptInstance) else it
            }
    }

    override suspend fun deleteScriptPackageInstance(scriptPackageInstance: ScriptPackageInstance) {
        entries.value = entries.value.filterNot { it.packageInstance.id == scriptPackageInstance.id }
    }

    override suspend fun getScriptPackageInstancesInGroup(scriptPackageGroup: ScriptPackageGroup): List<ScriptPackageInstance> = entries.value.inGroup(scriptPackageGroup.id)

    override suspend fun getScriptPackageInstancesInGroupFlow(groupId: String): Flow<List<ScriptPackageInstance>> = entries.map { it.inGroup(groupId) }

    override suspend fun updateScriptPackageInstanceGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) {
        entries.value = entries.value.map { if (it.packageInstance.id == scriptPackageInstance.id) it.copy(groupId = newGroupId) else it }
    }

    override suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    ) {
        entries.value = entries.value + Entry(scriptPackageInstance, groupId, mainScriptInstance)
    }

    override suspend fun getScriptPackageInstances(): List<ScriptPackageInstance> = entries.value.map { it.packageInstance }

    override suspend fun getScriptPackageInstances(packageName: String): List<ScriptPackageInstance> = getScriptPackageInstances()

    override suspend fun getScriptPackageInstance(id: String): ScriptPackageInstance = getScriptPackageInstances().first { it.id == id }

    override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> = entries.value.filter { it.packageInstance.id == scriptPackageInstance.id }.flatMap { listOfNotNull(it.mainScriptInstance) + it.childScriptInstances }

    private fun List<Entry>.inGroup(groupId: String) = filter { it.groupId == null || it.groupId == groupId }.map { it.packageInstance }
}
