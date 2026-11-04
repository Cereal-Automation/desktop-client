package com.cereal.client.application.interactor.agent

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.repository.GoalSharingRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The Recent checks of [Params.task], newest first: empty for a task that isn't the AI browser agent, and while Share
 * diagnostics is off, so the section stays hidden.
 */
@OptIn(FlowPreview::class)
class ObserveRecentChecksInteractor(
    private val failureReporter: FailureReporter,
    private val goalSharingRepository: GoalSharingRepository,
) : FlowInteractor<List<RunTrace>, ObserveRecentChecksInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<RunTrace>> =
        if (params.task.scriptInstance.packageInstance.definition.isAiBrowserAgent &&
            goalSharingRepository.isEnabled()
        ) {
            failureReporter.observeRecentChecks(params.task.id)
        } else {
            flowOf(emptyList())
        }

    data class Params(
        val task: Task,
    )
}
