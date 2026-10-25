package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.concurrentTaskCounts
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.mappers.taskStatusSortOrder
import com.cereal.client.presentation.tasks.mappers.toUiModel
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel

object TasksViewStateBuilder {
    /**
     * The task list from every group's task setups (in list order; null while they load) and all tasks.
     * Empty when there are no groups, or only one group and it holds no setups.
     *
     * With more than one group, [groupFilter] (a group id; null, or a group that is gone, means all groups) narrows the
     * list and the chip counts to that group. A [statusFilter] other than All then keeps only matching task rows and
     * drops setups (and groups) left without any; the chip counts ignore it.
     */
    fun buildListState(
        groups: List<Pair<ScriptPackageGroup, List<ScriptPackageInstance>>>?,
        tasks: List<Task>,
        statusFilter: TaskFilter = TaskFilter.ALL,
        groupFilter: String? = null,
    ): TaskViewState.ListState {
        if (groups == null) return TaskViewState.ListState.Loading
        if (groups.isEmpty() || (groups.size == 1 && groups.single().second.isEmpty())) return TaskViewState.ListState.Empty
        val pickedGroupId = groupFilter?.takeIf { id -> groups.size > 1 && groups.any { it.first.id == id } }
        val shownGroups = if (pickedGroupId == null) groups else groups.filter { it.first.id == pickedGroupId }
        val tasksBySetup = tasks.groupBy { it.scriptInstance.getScriptPackageInstance().id }
        return TaskViewState.ListState.Filled(
            groups =
                shownGroups
                    .map { (group, setups) ->
                        val sections = setups.map { buildSetupSection(it, tasksBySetup[it.id].orEmpty(), statusFilter) }
                        TaskGroupSection(group, group.name, if (statusFilter == TaskFilter.ALL) sections else sections.filter { it.scripts.isNotEmpty() })
                    }.filter { statusFilter == TaskFilter.ALL || it.setups.isNotEmpty() },
            setupCount = groups.sumOf { it.second.size },
            taskCount = groups.sumOf { (_, setups) -> setups.sumOf { tasksBySetup[it.id].orEmpty().size } },
            chips = chips(shownGroups.flatMap { (_, setups) -> setups.flatMap { tasksBySetup[it.id].orEmpty() } }, statusFilter),
            groupFilter = if (groups.size > 1) GroupFilterState(groups.map { (group) -> GroupFilterOption(group.id, group.name) }, pickedGroupId) else null,
        )
    }

    /** Every chip with its count over [tasks]; one with no tasks is left out unless it is All or [active]. */
    private fun chips(
        tasks: List<Task>,
        active: TaskFilter,
    ): List<TaskFilterChip> =
        TaskFilter.entries
            .map { filter -> TaskFilterChip(filter, tasks.count { filter.matches(it.status) }, active = filter == active) }
            .filter { it.filter == TaskFilter.ALL || it.active || it.count > 0 }

    /** [setup]'s row and its [tasks]' rows. */
    private fun buildSetupSection(
        setup: ScriptPackageInstance,
        tasks: List<Task>,
        statusFilter: TaskFilter,
    ): TaskSetupSection {
        val failed = tasks.count { it.status is TaskStatus.Error }
        val running = tasks.count { it.status is TaskStatus.Running }
        return TaskSetupSection(
            setup = setup,
            name = setup.definition.manifest.name,
            subtitle =
                setup.definition.mainScript.configuration
                    .getScriptIdentifierValue(setup.mainConfiguration),
            runningCount = running,
            failedCount = failed,
            taskCount = tasks.size,
            stepper = stepperOf(setup, tasks),
            showStopAll = running > 0,
            canStartAll = tasks.any { it.status is TaskStatus.Idle },
            menuOptions =
                buildList {
                    add(MenuOption.EDIT)
                    add(MenuOption.VIEW_CONFIGURATION)
                    add(MenuOption.DUPLICATE)
                    add(MenuOption.MOVE_TO_GROUP)
                    if (failed > 0) add(MenuOption.RESTART_FAILED)
                    if (setup.definition.manifest.supportUrl != null) add(MenuOption.CONTACT_SUPPORT)
                    add(MenuOption.DELETE)
                },
            scripts = scriptRows(tasks, statusFilter),
        )
    }

    /** [setup]'s `− count +` stepper; null when its count is fixed at one. [tasks] may include other setups' tasks. */
    fun stepperOf(
        setup: ScriptPackageInstance,
        tasks: List<Task>,
    ): ConcurrentTaskStepperState? =
        setup.concurrentTaskCounts(tasks)?.let { counts ->
            val count = setup.numberOfConcurrentTasks
            ConcurrentTaskStepperState(count, canDecrease = count - 1 >= counts.first, canIncrease = count + 1 <= counts.last)
        }

    /**
     * One setup's tasks matching [filter] as rows per script: numbered by creation within the setup (1-based), sorted
     * by status then number; the main script first, then child scripts newest first.
     */
    private fun scriptRows(
        tasks: List<Task>,
        filter: TaskFilter,
    ): List<ScriptInstanceUiModel> {
        val numbers = numberTasks(tasks)
        val scripts =
            tasks.filter { filter.matches(it.status) }.groupBy { it.scriptInstance.id }.values.map { scriptTasks ->
                scriptTasks
                    .first()
                    .scriptInstance
                    .toUiModel()
                    .apply {
                        this.tasks +=
                            scriptTasks
                                .map { it.toUiModel(numbers.getValue(it.id)) }
                                .sortedWith(compareBy<TaskUiModel>({ taskStatusSortOrder[it.id.status::class] ?: Int.MAX_VALUE }, { it.taskNumber }))
                    }
            }
        val (main, children) = scripts.partition { it.id.definition is MainScript }
        return main + children.sortedByDescending { it.id.createdAt.epochSeconds }
    }

    /** The inspector for [taskId], numbered as in the list; null when the task no longer exists. */
    fun buildInspectorState(
        taskId: String,
        tasks: List<Task>,
    ): TaskInspectorState? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        val setupId = task.scriptInstance.getScriptPackageInstance().id
        val number = numberTasks(tasks.filter { it.scriptInstance.getScriptPackageInstance().id == setupId }).getValue(taskId)
        val model = task.toUiModel(number)
        val actions =
            buildList {
                add(
                    when (task.status) {
                        is TaskStatus.Running -> TaskInspectorAction.STOP
                        is TaskStatus.Idle -> TaskInspectorAction.START
                        is TaskStatus.Error, is TaskStatus.Success -> TaskInspectorAction.RESTART
                    },
                )
                if (model.isError && model.hasSupportUrl) add(TaskInspectorAction.REPORT_ISSUE)
            }
        return TaskInspectorState(model, actions)
    }

    private fun numberTasks(tasksInSetup: List<Task>): Map<String, Int> = tasksInSetup.sortedBy { it.createdAt }.mapIndexed { index, task -> task.id to index + 1 }.toMap()

    /**
     * Pending interactions across ALL tasks: an interaction window that leaves composition cancels the interaction,
     * so every setup's windows stay open.
     */
    fun extractUserInteractions(tasks: List<Task>?): List<TaskUiModel> =
        tasks
            .orEmpty()
            .groupBy { it.scriptInstance.getScriptPackageInstance().id }
            .values
            .flatMap { tasksInSetup ->
                // Same per-setup numbering as the list rows.
                val numbers = numberTasks(tasksInSetup)
                tasksInSetup.sortedBy { it.createdAt }.filter { it.userInteraction != null }.map { it.toUiModel(numbers.getValue(it.id)) }
            }
}
