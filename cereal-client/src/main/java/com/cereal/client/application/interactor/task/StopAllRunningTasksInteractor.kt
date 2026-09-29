package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.flow.first

/**
 * Stops every running task through the normal stop path and waits for their jobs to finish, so each
 * task's `Idle` status is persisted before the caller goes on (e.g. before headless mode exits).
 */
class StopAllRunningTasksInteractor(
    private val taskManager: TaskManager,
    private val tasksRepository: TasksRepository,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None) {
        val running = tasksRepository.getAllTasks().first().filter { it.status.isRunning() }
        running.forEach { taskManager.stopTask(it.id) }
        running.forEach { tasksRepository.getTask(it.id)?.job?.join() }
    }
}
