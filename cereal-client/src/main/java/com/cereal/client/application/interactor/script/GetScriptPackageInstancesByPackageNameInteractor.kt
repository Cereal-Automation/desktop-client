package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository

class GetScriptPackageInstancesByPackageNameInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
) : Interactor<List<ScriptPackageInstance>, GetScriptPackageInstancesByPackageNameInteractor.Params>() {
    override suspend fun run(params: Params): List<ScriptPackageInstance> = scriptInstanceRepository.getScriptPackageInstances(params.packageName)

    data class Params(
        val packageName: String,
    )
}
