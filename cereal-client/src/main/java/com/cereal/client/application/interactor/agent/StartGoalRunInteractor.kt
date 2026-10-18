package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.agent.GoalRunInput
import com.cereal.client.domain.model.agent.supportsGoalComposer
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance

/**
 * Starts a goal run from the Home composer: one single-task instance of the AI browser agent in the
 * Default group, running immediately. Only the composer's four keys are set; every other item keeps
 * the script's default.
 */
class StartGoalRunInteractor(
    private val getOrCreateDefaultTaskGroupInteractor: GetOrCreateDefaultTaskGroupInteractor,
    private val startScriptInteractor: StartScriptInteractor,
    private val startAllTasksInScriptPackageInstanceInteractor: StartAllTasksInScriptPackageInstanceInteractor,
) : Interactor<ScriptPackageInstance, StartGoalRunInteractor.Params>() {
    override suspend fun run(params: Params): ScriptPackageInstance {
        val definition = params.agent.mainScript.configuration
        if (!definition.supportsGoalComposer()) throw GoalComposerUnsupportedException()

        val group = getOrCreateDefaultTaskGroupInteractor.run(Interactor.None())
        val instance =
            startScriptInteractor.run(
                StartScriptInteractor.Params(
                    groupId = group.id,
                    scriptPackage = params.agent,
                    mainScriptConfiguration = params.input.toConfiguration(definition),
                    childConfigurations = emptyMap(),
                    numberOfConcurrentTasks = 1,
                    ignoreConcurrencyConflicts = true,
                ),
            )
        startAllTasksInScriptPackageInstanceInteractor.run(StartAllTasksInScriptPackageInstanceInteractor.Params(instance))
        return instance
    }

    data class Params(
        val agent: ScriptPackage,
        val input: GoalRunInput,
    )
}

/** The installed agent no longer declares the composer's keys as the composer expects; use the generic form. */
class GoalComposerUnsupportedException : CerealException("This version of the AI browser agent can't be started from Home. Use More options instead.")
