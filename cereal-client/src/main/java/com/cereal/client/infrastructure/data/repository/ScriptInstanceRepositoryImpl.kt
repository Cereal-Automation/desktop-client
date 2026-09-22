package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.ScriptInstanceNotFoundException
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.getSupportUrlByPackageName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent

class ScriptInstanceRepositoryImpl(
    private val scriptInstanceDataSource: ScriptInstanceDataSource,
    private val fileSystemScriptsDataSource: FileSystemScriptsDataSource,
    private val userSession: UserSession,
    private val subscriptionDataSource: SubscriptionDataSource,
) : ScriptInstanceRepository,
    KoinComponent {
    override suspend fun updateScriptPackageInstanceGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) {
        val user = userSession.requireUser()
        scriptInstanceDataSource.updateScriptPackageInstanceGroup(user, scriptPackageInstance, newGroupId)
    }

    override suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    ) {
        scriptInstanceDataSource.addScriptPackageInstance(
            userSession.requireUser(),
            scriptPackageInstance,
            mainScriptInstance,
            groupId,
        )
    }

    override suspend fun addChildScriptInstance(
        parentInstance: ScriptInstance,
        name: String,
        scriptInstance: ChildScriptInstance,
    ) {
        scriptInstanceDataSource.addChildScript(
            userSession.requireUser(),
            name,
            scriptInstance,
            parentInstance.id,
        )
    }

    override suspend fun getScriptPackageInstancesInGroup(scriptPackageGroup: ScriptPackageGroup): List<ScriptPackageInstance> {
        val user = userSession.requireUser()
        val scriptPackageDefinitions =
            subscriptionDataSource.enrichByPackageName(
                fileSystemScriptsDataSource.getScriptDefinitions(user).first(),
            )
        return scriptInstanceDataSource
            .getScriptPackageInstancesInGroup(
                user,
                scriptPackageGroup.id,
                scriptPackageDefinitions,
            )
    }

    override suspend fun deleteScriptPackageInstance(scriptPackageInstance: ScriptPackageInstance) {
        getScriptInstances(scriptPackageInstance).forEach {
            getKoin().getScopeOrNull(it.id)?.close()
        }

        scriptInstanceDataSource.deleteScriptPackageInstance(
            userSession.requireUser(),
            scriptPackageInstance.id,
        )
        getKoin().getScopeOrNull(scriptPackageInstance.id)?.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getScriptPackageInstancesInGroupFlow(groupId: String): Flow<List<ScriptPackageInstance>> {
        val user = userSession.requireUser()
        // Fetch the support-URL map once and reuse it across emissions rather than re-querying the
        // back end every time the filesystem definitions change.
        val supportUrlByPackageName = subscriptionDataSource.getSupportUrlByPackageName()
        val scriptDefinitionsFlow =
            fileSystemScriptsDataSource
                .getScriptDefinitions(user)
                .map { scriptPackages -> scriptPackages.toScriptPackagesByPackageName(supportUrlByPackageName) }

        return scriptDefinitionsFlow.flatMapLatest { scriptPackageDefinitions ->
            scriptInstanceDataSource
                .getScriptPackagesInGroupFlow(
                    user,
                    groupId,
                    scriptPackageDefinitions,
                )
        }
    }

    override suspend fun getScriptPackageInstances(): List<ScriptPackageInstance> {
        val user = userSession.requireUser()
        val scriptPackageDefinitions =
            subscriptionDataSource.enrichByPackageName(
                fileSystemScriptsDataSource.getScriptDefinitions(user).first(),
            )
        return scriptInstanceDataSource
            .getScriptPackageInstances(user, scriptPackageDefinitions)
    }

    override suspend fun getScriptPackageInstances(packageName: String): List<ScriptPackageInstance> =
        getScriptPackageInstances().filter {
            it.definition.manifest.packageName == packageName
        }

    override suspend fun getScriptPackageInstance(id: String): ScriptPackageInstance {
        val user = userSession.requireUser()

        // First, get the package name for the script package
        val packageName =
            scriptInstanceDataSource.getScriptPackageNameById(user, id)
                ?: throw ScriptInstanceNotFoundException()

        // Get the script package definition, enriched with its support URL.
        val scriptPackageDefinition =
            fileSystemScriptsDataSource
                .getScriptPackageDefinition(packageName, user)
                ?.let { subscriptionDataSource.enrich(it) }
                ?: throw ScriptInstanceNotFoundException()

        return scriptInstanceDataSource
            .getScriptPackageInstance(
                user,
                id,
                scriptPackageDefinition,
            ) ?: throw ScriptInstanceNotFoundException()
    }

    override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> =
        scriptInstanceDataSource.getScriptInstances(
            userSession.requireUser(),
            scriptPackageInstance,
        )
}
