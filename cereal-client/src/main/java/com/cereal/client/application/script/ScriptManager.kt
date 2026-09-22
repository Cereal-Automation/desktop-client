package com.cereal.client.application.script

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptRepository

class ScriptManager(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val scriptRepository: ScriptRepository,
    private val scriptInstallProvider: ScriptInstallProvider,
    private val scriptInstanceManager: ScriptInstanceManager,
) {
    suspend fun deleteScript(scriptPackage: ScriptPackage) {
        // Delete all script instances that use this script.
        scriptInstanceRepository
            .getScriptPackageInstances(scriptPackage.manifest.packageName)
            .forEach { scriptInstance ->
                scriptInstanceManager.deleteScriptInstance(scriptInstance)
            }

        scriptRepository.removeScript(scriptPackage)
    }

    suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ) {
        // Download the updated version of the script.
        scriptInstallProvider.updateScript(scriptPackage, release)

        // Reload all script instances.
        scriptInstanceRepository
            .getScriptPackageInstances(scriptPackage.manifest.packageName)
            .forEach { scriptInstance ->
                scriptInstanceManager.reloadScriptInstance(scriptInstance)
            }
    }
}
