package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.script.ScriptSyncManager
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.flow.first

class SetDevelopmentScriptsInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val scriptSyncManager: ScriptSyncManager,
) : Interactor<Unit, SetDevelopmentScriptsInteractor.Params>() {
    override suspend fun run(params: Params) {
        val currentValue = applicationPreferenceRepository.isDevelopmentScriptsEnabled().first()

        if (currentValue != params.enabled) {
            // Update value before sync so that any code executed after this statement can use that property.
            applicationPreferenceRepository.setDevelopmentScriptsEnabled(params.enabled)

            // Note: it's taken for granted that updating scripts means that the script is stopped. Since only
            // developers should use this it shouldn't be a big problem for the ones using this setting.
            val result = scriptSyncManager.sync(updateScripts = true)

            if (result.isNotEmpty()) {
                applicationPreferenceRepository.setDevelopmentScriptsEnabled(!params.enabled)
                throw ScriptSyncException(result)
            }
        }
    }

    data class Params(
        val enabled: Boolean,
    )
}
