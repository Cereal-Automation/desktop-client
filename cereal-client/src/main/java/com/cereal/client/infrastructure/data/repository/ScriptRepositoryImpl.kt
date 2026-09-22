package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.getSupportUrlByPackageName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScriptRepositoryImpl(
    private val fileSystemScriptsDataSource: FileSystemScriptsDataSource,
    private val userSession: UserSession,
    private val subscriptionDataSource: SubscriptionDataSource,
) : ScriptRepository {
    override suspend fun getInstalledScripts(): Flow<List<ScriptPackage>> {
        val user = userSession.requireUser()
        val supportUrlByPackageName = subscriptionDataSource.getSupportUrlByPackageName()
        return fileSystemScriptsDataSource.getScriptDefinitions(user).map { definitions ->
            definitions.toScriptPackages(supportUrlByPackageName)
        }
    }

    override suspend fun getScript(packageName: String): ScriptPackage? {
        val user = userSession.requireUser()
        val definition = fileSystemScriptsDataSource.getScriptPackageDefinition(packageName, user) ?: return null
        return subscriptionDataSource.enrich(definition)
    }

    override suspend fun removeScript(scriptPackage: ScriptPackage) {
        fileSystemScriptsDataSource.deleteScript(scriptPackage, userSession.requireUser())
    }
}
