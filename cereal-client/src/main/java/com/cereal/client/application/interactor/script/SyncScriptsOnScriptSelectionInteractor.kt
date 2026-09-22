package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.script.ScriptSyncManager

class SyncScriptsOnScriptSelectionInteractor(
    private val scriptSyncManager: ScriptSyncManager,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: None) {
        // Do not update scripts because this would mean currently running tasks are stopped and we don't want that here.
        val result = scriptSyncManager.sync(updateScripts = false)

        if (result.isNotEmpty()) {
            throw ScriptSyncException(result)
        }
    }
}
