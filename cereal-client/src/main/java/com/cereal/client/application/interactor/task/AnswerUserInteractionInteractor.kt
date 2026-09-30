package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.TasksRepository
import kotlin.coroutines.resume

/** Answers a task's pending text-input or continue interaction, so the task carries on. */
class AnswerUserInteractionInteractor(
    private val tasksRepository: TasksRepository,
) : Interactor<Unit, AnswerUserInteractionInteractor.Params>() {
    override suspend fun run(params: Params) {
        val interaction = tasksRepository.getTask(params.taskId)?.userInteraction
        val answer: () -> Unit =
            when {
                params is Params.Text && interaction is UserInteraction.TextInput -> {
                    { interaction.continuation.resume(params.text) }
                }

                params is Params.Continue && interaction is UserInteraction.ContinueButton -> {
                    { interaction.continuation.resume(Unit) }
                }

                else -> {
                    throw CerealException("This task is no longer waiting for that.")
                }
            }
        // Clear before resuming: the resumed task may ask again straight away, and that must survive.
        tasksRepository.setUserInteraction(params.taskId, null)
        answer()
    }

    sealed class Params(
        val taskId: String,
    ) {
        class Text(
            taskId: String,
            val text: String,
        ) : Params(taskId)

        class Continue(
            taskId: String,
        ) : Params(taskId)
    }
}
