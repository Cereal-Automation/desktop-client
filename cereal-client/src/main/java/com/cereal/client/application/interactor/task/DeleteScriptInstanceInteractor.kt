package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.ScriptInstanceManager
import com.cereal.client.domain.model.script.ScriptPackageInstance

class DeleteScriptInstanceInteractor(
    private val scriptInstanceManager: ScriptInstanceManager,
) : Interactor<Unit, DeleteScriptInstanceInteractor.Params>() {
    override suspend fun run(params: Params) {
        scriptInstanceManager.deleteScriptInstance(params.scriptPackageInstance)
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}
