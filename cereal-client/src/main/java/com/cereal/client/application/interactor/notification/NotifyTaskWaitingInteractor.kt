package com.cereal.client.application.interactor.notification

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskId
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Headless only: sends "Task waiting for you" once for each new pending user interaction, through
 * the script-instance send so the script's overrides apply and it is recorded against the task. The
 * message names the script, task number, host name and kind, never a prompt's title, address or
 * password. Emits after each task-list change it has handled.
 */
@OptIn(FlowPreview::class)
class NotifyTaskWaitingInteractor(
    private val tasksRepository: TasksRepository,
    private val sendNotificationFromScriptInstanceInteractor: SendNotificationFromScriptInstanceInteractor,
    private val systemProvider: SystemProvider,
) : FlowInteractor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Unit> {
        // By identity: every prompt is a new instance, even one asking the same thing again.
        var pending = emptyMap<TaskId, UserInteraction>()
        return tasksRepository.getAllTasks().map { tasks ->
            val fresh = tasks.filter { task -> task.userInteraction.let { it != null && it !== pending[task.id] } }
            pending = tasks.mapNotNull { task -> task.userInteraction?.let { task.id to it } }.toMap()
            fresh.forEach { notify(it, tasks) }
        }
    }

    private suspend fun notify(
        task: Task,
        tasks: List<Task>,
    ) {
        val pkg = task.scriptInstance.getScriptPackageInstance()
        // Task numbers as on the desktop: a package's tasks by creation order, from 1.
        val number =
            tasks
                .filter { it.scriptInstance.getScriptPackageInstance().id == pkg.id }
                .sortedBy { it.createdAt }
                .indexOfFirst { it.id == task.id } + 1
        val kind =
            when (task.userInteraction) {
                is UserInteraction.Browser -> "browser prompt"
                is UserInteraction.TextInput -> "text input"
                is UserInteraction.ContinueButton, null -> "continue"
            }
        sendNotificationFromScriptInstanceInteractor(
            SendNotificationFromScriptInstanceInteractor.Params(
                notification =
                    ScriptNotification(
                        title = TITLE,
                        message = "${pkg.definition.manifest.name} #$number on ${systemProvider.hostname()} is waiting for you ($kind).",
                    ),
                taskId = task.id,
                scriptPackageInstance = pkg,
            ),
        )
    }

    companion object {
        const val TITLE = "Task waiting for you"
    }
}
