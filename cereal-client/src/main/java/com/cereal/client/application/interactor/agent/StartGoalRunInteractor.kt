package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.GoalRunInput
import com.cereal.client.application.agent.supportsGoalComposer
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance

/**
 * Starts a goal run from the Home composer: one single-task instance of the AI browser agent in the
 * Default group, running immediately. Only the composer's four keys are set; every other item keeps
 * the script's default.
 *
 * Like the generic launch, it first checks for a notification channel: without one it throws
 * [NoNotificationChannelsException] (nothing created) unless [Params.ignoreMissingNotificationChannels].
 */
class StartGoalRunInteractor(
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor,
    private val getOrCreateDefaultTaskGroupInteractor: GetOrCreateDefaultTaskGroupInteractor,
    private val startScriptInteractor: StartScriptInteractor,
    private val startAllTasksInScriptPackageInstanceInteractor: StartAllTasksInScriptPackageInstanceInteractor,
) : Interactor<ScriptPackageInstance, StartGoalRunInteractor.Params>() {
    override suspend fun run(params: Params): ScriptPackageInstance {
        val definition = params.agent.mainScript.configuration
        if (!definition.supportsGoalComposer()) throw GoalComposerUnsupportedException()
        if (!params.ignoreMissingNotificationChannels &&
            !hasNotificationChannelsConfiguredInteractor.run(HasNotificationChannelsConfiguredInteractor.Params(null))
        ) {
            throw NoNotificationChannelsException()
        }

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
        /** The user has seen the no-notification-channels warning and chose to start anyway. */
        val ignoreMissingNotificationChannels: Boolean = false,
    )
}

/** The installed agent no longer declares the composer's keys as the composer expects; use the generic form. */
class GoalComposerUnsupportedException : CerealException("This version of the AI browser agent can't be started from Home. Use More options instead.")

/** No notification channel is set up, so the user wouldn't hear about the run; warn before starting. */
class NoNotificationChannelsException : CerealException("No notification channel is configured.")
