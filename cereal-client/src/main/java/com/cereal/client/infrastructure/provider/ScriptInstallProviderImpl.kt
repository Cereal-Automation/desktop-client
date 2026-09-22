package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ApplicationRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.toScriptPackage
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.repository.enrich
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScriptInstallProviderImpl(
    private val fileSystemScriptsDataSource: FileSystemScriptsDataSource,
    private val marketplaceDataSource: MarketplaceDataSource,
    private val userSession: UserSession,
    private val subscriptionDataSource: SubscriptionDataSource,
    private val applicationRepository: ApplicationRepository,
) : ScriptInstallProvider {
    override suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ): ScriptPackage =
        withContext(Dispatchers.IO) {
            val definition =
                marketplaceDataSource
                    .downloadScript(scriptPackage.manifest.packageName, release.versionCode)
                    .use { inputStream ->
                        fileSystemScriptsDataSource.updateScript(
                            scriptPackage,
                            release,
                            inputStream,
                            userSession.requireUser(),
                            applicationRepository.getSdkVersion(),
                        )
                    }
            definition.toScriptPackage(scriptPackage.manifest.supportUrl)
        }

    override suspend fun installScript(
        packageName: String,
        release: Release,
    ): ScriptPackage =
        withContext(Dispatchers.IO) {
            val definition =
                marketplaceDataSource.downloadScript(packageName, release.versionCode).use { inputStream ->
                    fileSystemScriptsDataSource.storeScript(
                        packageName,
                        release,
                        inputStream,
                        userSession.requireUser(),
                        applicationRepository.getSdkVersion(),
                    )
                }
            subscriptionDataSource.invalidateCache()
            subscriptionDataSource.enrich(definition)
        }
}
