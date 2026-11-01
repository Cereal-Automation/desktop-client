package com.cereal.client.application.script

import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScriptManager(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val scriptRepository: ScriptRepository,
    private val scriptInstallProvider: ScriptInstallProvider,
    private val scriptInstanceManager: ScriptInstanceManager,
) {
    private val _releasesRequiringClientUpdate = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Version code per package of a release whose update was rejected because it needs a newer client; the installed
     * version is kept. An installed version at or above it no longer needs that release.
     */
    val releasesRequiringClientUpdate: StateFlow<Map<String, Long>> = _releasesRequiringClientUpdate.asStateFlow()

    suspend fun deleteScript(scriptPackage: ScriptPackage) {
        // Delete all script instances that use this script.
        scriptInstanceRepository
            .getScriptPackageInstances(scriptPackage.manifest.packageName)
            .forEach { scriptInstance ->
                scriptInstanceManager.deleteScriptInstance(scriptInstance)
            }

        scriptRepository.removeScript(scriptPackage)
        _releasesRequiringClientUpdate.update { it - scriptPackage.manifest.packageName }
    }

    suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ) {
        val packageName = scriptPackage.manifest.packageName
        // Download the updated version of the script.
        try {
            scriptInstallProvider.updateScript(scriptPackage, release)
        } catch (e: ClientUpdateRequiredException) {
            _releasesRequiringClientUpdate.update { it + (packageName to release.versionCode) }
            throw e
        }
        _releasesRequiringClientUpdate.update { it - packageName }

        // Reload all script instances.
        scriptInstanceRepository
            .getScriptPackageInstances(packageName)
            .forEach { scriptInstance ->
                scriptInstanceManager.reloadScriptInstance(scriptInstance)
            }
    }
}
