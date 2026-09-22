package com.cereal.client.domain.repository

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import kotlinx.coroutines.flow.Flow

interface ScriptInstanceRepository {
    suspend fun addChildScriptInstance(
        parentInstance: ScriptInstance,
        name: String,
        scriptInstance: ChildScriptInstance,
    )

    suspend fun deleteScriptPackageInstance(scriptPackageInstance: ScriptPackageInstance)

    suspend fun getScriptPackageInstancesInGroup(scriptPackageGroup: ScriptPackageGroup): List<ScriptPackageInstance>

    suspend fun getScriptPackageInstancesInGroupFlow(groupId: String): Flow<List<ScriptPackageInstance>>

    suspend fun updateScriptPackageInstanceGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    )

    suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    )

    suspend fun getScriptPackageInstances(): List<ScriptPackageInstance>

    suspend fun getScriptPackageInstances(packageName: String): List<ScriptPackageInstance>

    suspend fun getScriptPackageInstance(id: String): ScriptPackageInstance

    suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance>
}
