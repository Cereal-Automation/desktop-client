package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager

/**
 * Stops every running task and waits for their jobs to finish, so each task's `Idle` status is
 * persisted before the caller goes on (e.g. before headless mode exits).
 */
class StopAllRunningTasksInteractor(
    private val taskManager: TaskManager,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None) {
        taskManager.stopAllTasks()
    }
}
