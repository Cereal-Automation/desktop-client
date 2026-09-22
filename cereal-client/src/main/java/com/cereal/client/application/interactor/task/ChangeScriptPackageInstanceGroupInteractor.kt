package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository

class ChangeScriptPackageInstanceGroupInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
) : Interactor<Unit, ChangeScriptPackageInstanceGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        scriptInstanceRepository.updateScriptPackageInstanceGroup(
            scriptPackageInstance = params.scriptPackageInstance,
            newGroupId = params.newGroupId,
        )
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val newGroupId: String,
    )
}
