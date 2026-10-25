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
import kotlin.time.ExperimentalTime

/**
 * In-memory [ScriptInstanceRepository] for the mock flavor and tests. Starts empty and keeps each
 * package instance's group and main script, like the Room-backed repository. Child scripts are not modelled.
 */
class InMemoryScriptInstanceRepository : ScriptInstanceRepository {
    private data class Entry(
        val groupId: String,
        val packageInstance: ScriptPackageInstance,
        val mainScript: MainScriptInstance,
    )

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Replaces the contents; each instance gets a main script with id `script-<id>` (as `fixtures.aScriptInstance`). */
    @OptIn(ExperimentalTime::class)
    fun seed(
        packageInstances: List<ScriptPackageInstance>,
        groupId: String = "default",
    ) {
        entries.value =
            packageInstances.map {
                val main =
                    MainScriptInstance(
                        id = "script-${it.id}",
                        definition = it.definition.mainScript,
                        configuration = it.mainConfiguration,
                        createdAt = it.createdAt,
                        packageInstance = it,
                    )
                Entry(groupId, it, main)
            }
    }

    override suspend fun addChildScriptInstance(
        parentInstance: ScriptInstance,
        name: String,
        scriptInstance: ChildScriptInstance,
    ) = Unit

    override suspend fun deleteScriptPackageInstance(scriptPackageInstance: ScriptPackageInstance) {
        entries.value = entries.value.filterNot { it.packageInstance.id == scriptPackageInstance.id }
    }

    override suspend fun getScriptPackageInstancesInGroup(scriptPackageGroup: ScriptPackageGroup): List<ScriptPackageInstance> = entries.value.inGroup(scriptPackageGroup.id)

    override suspend fun getScriptPackageInstancesInGroupFlow(groupId: String): Flow<List<ScriptPackageInstance>> = entries.map { it.inGroup(groupId) }

    override suspend fun updateScriptPackageInstanceGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) {
        entries.value =
            entries.value.map {
                if (it.packageInstance.id == scriptPackageInstance.id) it.copy(groupId = newGroupId) else it
            }
    }

    override suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    ) {
        entries.value = entries.value + Entry(groupId, scriptPackageInstance, mainScriptInstance)
    }

    override suspend fun getScriptPackageInstances(): List<ScriptPackageInstance> = entries.value.map { it.packageInstance }

    override suspend fun getScriptPackageInstances(packageName: String): List<ScriptPackageInstance> = getScriptPackageInstances().filter { it.definition.manifest.packageName == packageName }

    override suspend fun getScriptPackageInstance(id: String): ScriptPackageInstance = getScriptPackageInstances().first { it.id == id }

    override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> = entries.value.filter { it.packageInstance.id == scriptPackageInstance.id }.map { it.mainScript }

    private fun List<Entry>.inGroup(groupId: String) = filter { it.groupId == groupId }.map { it.packageInstance }
}
