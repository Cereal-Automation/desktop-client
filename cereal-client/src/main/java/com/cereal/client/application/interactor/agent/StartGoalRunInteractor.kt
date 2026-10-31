package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance

/**
 * Starts a goal run from the Home composer: one single-task instance of the AI browser agent in the
 * Default group, running immediately, with the composer's configuration.
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
                    variantId = params.variantId,
                    mainScriptConfiguration = params.mainConfiguration,
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
        /** Every value of the entry's script: what the composer shows plus the defaults it keeps. */
        val mainConfiguration: ScriptConfigurationValues,
        /** The user has seen the no-notification-channels warning and chose to start anyway. */
        val ignoreMissingNotificationChannels: Boolean = false,
        /** The agent's variant to run; null only for an agent without variants. */
        val variantId: String? = null,
    )
}

/** No notification channel is set up, so the user wouldn't hear about the run; warn before starting. */
class NoNotificationChannelsException : CerealException("No notification channel is configured.")
