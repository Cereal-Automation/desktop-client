package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.ScriptInstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ScriptInstanceRepository] for screen tests. Starts empty; group/package queries
 * return the full set since the fixture does not model grouping.
 */
class InMemoryScriptInstanceRepository : ScriptInstanceRepository {
    private val instances = MutableStateFlow<List<ScriptPackageInstance>>(emptyList())

    fun seed(packageInstances: List<ScriptPackageInstance>) {
        instances.value = packageInstances
    }

    override suspend fun addChildScriptInstance(
        parentInstance: ScriptInstance,
        name: String,
        scriptInstance: ChildScriptInstance,
    ) = Unit

    override suspend fun deleteScriptPackageInstance(scriptPackageInstance: ScriptPackageInstance) {
        instances.value = instances.value.filterNot { it.id == scriptPackageInstance.id }
    }

    override suspend fun getScriptPackageInstancesInGroup(scriptPackageGroup: ScriptPackageGroup): List<ScriptPackageInstance> = instances.value

    override suspend fun getScriptPackageInstancesInGroupFlow(groupId: String): Flow<List<ScriptPackageInstance>> = instances

    override suspend fun updateScriptPackageInstanceGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) = Unit

    override suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    ) {
        instances.value = instances.value + scriptPackageInstance
    }

    override suspend fun getScriptPackageInstances(): List<ScriptPackageInstance> = instances.value

    override suspend fun getScriptPackageInstances(packageName: String): List<ScriptPackageInstance> = instances.value

    override suspend fun getScriptPackageInstance(id: String): ScriptPackageInstance = instances.value.first { it.id == id }

    override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> = emptyList()
}
