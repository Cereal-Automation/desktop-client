package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.mappers.taskStatusSortOrder
import com.cereal.client.presentation.tasks.mappers.toUiModel
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel

object TasksViewStateBuilder {
    fun buildDetailViewState(
        tasks: List<Task>?,
        selectedScriptPackageInstance: ScriptPackageInstance?,
    ): TaskViewState.DetailViewState {
        if (selectedScriptPackageInstance == null) {
            return TaskViewState.DetailViewState.NoSelection
        }

        // Filter tasks by group and script.
        val filteredTasks =
            tasks?.filter {
                it.scriptInstance.getScriptPackageInstance().id == selectedScriptPackageInstance.id
            }

        val scriptInstances = mutableListOf<ScriptInstanceUiModel>()

        // Group tasks per script instance and assign task numbers per ScriptPackageInstance
        val allTasksInPackage = filteredTasks?.sortedBy { it.createdAt } ?: emptyList()
        val taskNumberMap = allTasksInPackage.mapIndexed { index, task -> task.id to (index + 1) }.toMap()

        filteredTasks?.forEach { task ->
            val scriptInstance = task.scriptInstance
            val taskNumber = taskNumberMap[task.id] ?: 1
            scriptInstances.firstOrNull { it.id.id == scriptInstance.id }?.tasks?.add(task.toUiModel(taskNumber))
                ?: run {
                    val scriptInstanceUiModel = scriptInstance.toUiModel()
                    scriptInstanceUiModel.tasks.add(task.toUiModel(taskNumber))
                    scriptInstances.add(scriptInstanceUiModel)
                }
        }

        scriptInstances.forEach { scriptInstance ->
            scriptInstance.tasks.sortWith(
                compareBy<TaskUiModel> { task ->
                    taskStatusSortOrder[task.id.status::class] ?: Int.MAX_VALUE
                }.thenBy { task ->
                    // Sort by task number within each status group
                    task.taskNumber
                },
            )
        }

        // Sort child scripts, keep main script at beginning of list.
        val sortedScriptInstances =
            scriptInstances.firstOrNull { it.id.definition is MainScript }?.let { mainScriptInstance ->
                scriptInstances.remove(mainScriptInstance)
                listOf(mainScriptInstance) +
                    scriptInstances.sortedByDescending { childScriptInstance ->
                        childScriptInstance.id.createdAt.epochSeconds
                    }
            }

        val scriptName = selectedScriptPackageInstance.definition.manifest.name
        val scriptIdentifier =
            selectedScriptPackageInstance.definition.mainScript.configuration
                .getScriptIdentifierValue(selectedScriptPackageInstance.mainConfiguration)

        return if (!sortedScriptInstances.isNullOrEmpty()) {
            val isAnyTaskRunning =
                sortedScriptInstances.any { it.tasks.any { task -> task.id.status is TaskStatus.Running } }
            val isAnyTaskIdle =
                sortedScriptInstances.any { it.tasks.any { task -> task.id.status is TaskStatus.Idle } }

            val finishedTasksCount =
                sortedScriptInstances.sumOf { it.tasks.count { task -> task.id.status is TaskStatus.Success } }
            val erroredTasksCount =
                sortedScriptInstances.sumOf { it.tasks.count { task -> task.id.status is TaskStatus.Error } }
            val idleTasksCount =
                sortedScriptInstances.sumOf { it.tasks.count { task -> task.id.status is TaskStatus.Idle } }
            val runningTasksCount =
                sortedScriptInstances.sumOf { it.tasks.count { task -> task.id.status is TaskStatus.Running } }

            TaskViewState.DetailViewState.Filled(
                sortedScriptInstances,
                stopAllTasksEnabled = isAnyTaskRunning,
                startAllTasksEnabled = isAnyTaskIdle,
                headerTitle = scriptName,
                headerSubtitle = scriptIdentifier,
                finishedTasksCount = finishedTasksCount,
                erroredTasksCount = erroredTasksCount,
                idleTasksCount = idleTasksCount,
                runningTasksCount = runningTasksCount,
            )
        } else {
            TaskViewState.DetailViewState.Empty(
                headerTitle = scriptName,
                headerSubtitle = scriptIdentifier,
            )
        }
    }

    fun extractUserInteractions(detailViewState: TaskViewState.DetailViewState): List<TaskUiModel> {
        val taskUiModelsWithUserInteractions = mutableListOf<TaskUiModel>()

        (detailViewState as? TaskViewState.DetailViewState.Filled)?.scriptInstances?.forEach { scriptInstanceUiModel ->
            scriptInstanceUiModel.tasks.forEach { taskUiModel ->
                if (taskUiModel.id.userInteraction != null) {
                    taskUiModelsWithUserInteractions.add(taskUiModel)
                }
            }
        }

        return taskUiModelsWithUserInteractions
    }
}
