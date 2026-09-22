package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Persistence operations for script package instances and their child scripts.
 */
interface ScriptPackageInstanceDataSource {
    suspend fun updateScriptPackageInstanceGroup(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    )

    suspend fun addScriptPackageInstance(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
        groupId: String,
    )

    suspend fun addChildScript(
        user: User,
        childScriptIdentifier: String,
        scriptInstance: ChildScriptInstance,
        parentInstanceId: String,
    )

    suspend fun getScriptPackageInstancesInGroup(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance>

    fun getScriptPackagesInGroupFlow(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): Flow<List<ScriptPackageInstance>>

    suspend fun getScriptPackageInstances(
        user: User,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance>

    suspend fun getScriptPackageInstance(
        user: User,
        id: String,
        scriptPackageDefinition: ScriptPackage,
    ): ScriptPackageInstance?

    suspend fun getScriptPackageNameById(
        user: User,
        id: String,
    ): String?

    suspend fun deleteScriptPackageInstance(
        user: User,
        scriptPackageInstanceId: String,
    )

    suspend fun getScriptInstances(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
    ): List<ScriptInstance>
}
