package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** What the agent's generic config form opens with: the Default group and an unsaved instance prefilled from the composer. */
data class GoalRunFormPrefill(
    val group: ScriptPackageGroup,
    val instance: ScriptPackageInstance,
)

/**
 * Prepares Home's "More options": the agent's generic config form in the Default group, prefilled with
 * the composer's configuration. Nothing is saved until the form launches.
 */
class PrefillGoalRunFormInteractor(
    private val getOrCreateDefaultTaskGroupInteractor: GetOrCreateDefaultTaskGroupInteractor,
) : Interactor<GoalRunFormPrefill, PrefillGoalRunFormInteractor.Params>() {
    @OptIn(ExperimentalTime::class)
    override suspend fun run(params: Params): GoalRunFormPrefill {
        val group = getOrCreateDefaultTaskGroupInteractor.run(Interactor.None())
        val instance =
            ScriptPackageInstance(
                id = UUID.randomUUID().toString(),
                mainConfiguration = params.mainConfiguration,
                childConfigurations = emptyMap(),
                definition = params.agent,
                createdAt = Clock.System.now(),
                numberOfConcurrentTasks = 1,
                variantId = params.variantId,
            )
        return GoalRunFormPrefill(group, instance)
    }

    data class Params(
        val agent: ScriptPackage,
        val mainConfiguration: ScriptConfigurationValues,
        /** The agent's variant the composer is on; null for its default entry. */
        val variantId: String? = null,
    )
}
