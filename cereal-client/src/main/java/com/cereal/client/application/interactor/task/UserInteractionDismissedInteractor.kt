package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.TasksRepository

class UserInteractionDismissedInteractor(
    private val tasksRepository: TasksRepository,
) : Interactor<Unit, UserInteractionDismissedInteractor.Params>() {
    override suspend fun run(params: Params) = tasksRepository.setUserInteraction(params.taskId, null)

    data class Params(
        val taskId: String,
    )
}
