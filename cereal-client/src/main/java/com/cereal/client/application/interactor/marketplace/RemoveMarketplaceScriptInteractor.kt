package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.ScriptHasRunningTasksException
import com.cereal.client.application.script.ScriptManager
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

class RemoveMarketplaceScriptInteractor(
    private val marketplaceRepository: MarketplaceProvider,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
    private val scriptManager: ScriptManager,
) : Interactor<Unit, RemoveMarketplaceScriptInteractor.Params>() {
    override suspend fun run(params: Params) {
        val packageName = params.scriptPackage.manifest.packageName

        val instances = scriptInstanceRepository.getScriptPackageInstances(packageName)
        val anyRunning =
            instances.any { instance ->
                tasksRepository.getTasks(instance).any { it.status.isRunning() }
            }
        if (anyRunning) {
            throw ScriptHasRunningTasksException()
        }

        marketplaceRepository.unsubscribeFromScript(packageName)
        scriptManager.deleteScript(params.scriptPackage)
    }

    data class Params(
        val scriptPackage: ScriptPackage,
    )
}
